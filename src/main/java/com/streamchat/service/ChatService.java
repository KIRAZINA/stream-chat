package com.streamchat.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.streamchat.exception.RateLimitException;
import com.streamchat.exception.UnauthorizedException;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.dto.ChatHistoryResponse;
import com.streamchat.model.dto.MessageFragmentDTO;
import com.streamchat.model.entity.ChatMessage;
import com.streamchat.model.entity.Emote;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.MessageType;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.EmoteRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserBadgeRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for handling chat message operations.
 * Manages message persistence, caching, and real-time delivery.
 */
@Service
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final RateLimitService rateLimitService;
    private final ModerationService moderationService;
    private final StreamAuthorizationService streamAuthorizationService;
    private final UserStreamRoleRepository userStreamRoleRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final EmoteRepository emoteRepository;
    private final EmoteService emoteService;
    private final MetricsService metricsService;
    private final AutoModService autoModService;
    private final ChatMessagePersister chatMessagePersister;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final Cache<String, List<String>> userBadgeCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    private final Cache<String, List<MessageFragmentDTO>> emoteFragmentCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    private record BadgeCacheKey(Long userId, Long streamId) {
        @Override
        public int hashCode() {
            return Objects.hash(userId, streamId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            BadgeCacheKey other = (BadgeCacheKey) obj;
            return Objects.equals(userId, other.userId) && Objects.equals(streamId, other.streamId);
        }
    }

    /**
     * Atomic slow-mode check-and-set script.
     * Returns the remaining wait in milliseconds (0 = allowed, timestamp
     * updated) and otherwise leaves the existing timestamp untouched.
     * ARGV: [1] now (ms), [2] slow-mode window (ms), [3] TTL (seconds).
     */
    static final String SLOW_MODE_LUA = """
            local last = redis.call('GET', KEYS[1])
            if last then
              local now = tonumber(ARGV[1])
              local slowMs = tonumber(ARGV[2])
              local elapsed = now - tonumber(last)
              if elapsed < slowMs then
                return slowMs - elapsed
              end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[3]))
            return 0
            """;

    private static final byte[] SLOW_MODE_SCRIPT_BYTES = SLOW_MODE_LUA.getBytes(StandardCharsets.UTF_8);

    @Value("${chat.slowmode.storage:redis}")
    private String slowModeStorage;

    /**
     * In-memory slow-mode store used only when chat.slowmode.storage=memory
     * (dev escape hatch). Redis remains the single source of truth in prod.
     */
    private final Cache<String, SlowModeEntry> slowModeMemoryCache = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, SlowModeEntry>() {
                @Override
                public long expireAfterCreate(String key, SlowModeEntry value, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
                }

                @Override
                public long expireAfterUpdate(String key, SlowModeEntry value, long currentTime,
                                              long currentDuration) {
                    return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
                }

                @Override
                public long expireAfterRead(String key, SlowModeEntry value, long currentTime,
                                            long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    private record SlowModeEntry(long lastAtMs, long ttlSeconds) {
    }

    public ChatService(ChatMessageRepository chatMessageRepository,
                       StreamRepository streamRepository,
                       UserRepository userRepository,
                       RateLimitService rateLimitService,
                       ModerationService moderationService,
                       StreamAuthorizationService streamAuthorizationService,
                       UserStreamRoleRepository userStreamRoleRepository,
                       UserBadgeRepository userBadgeRepository,
                       EmoteRepository emoteRepository,
                       EmoteService emoteService,
                       MetricsService metricsService,
                       AutoModService autoModService,
                       ChatMessagePersister chatMessagePersister) {
        this.chatMessageRepository = chatMessageRepository;
        this.streamRepository = streamRepository;
        this.userRepository = userRepository;
        this.rateLimitService = rateLimitService;
        this.moderationService = moderationService;
        this.streamAuthorizationService = streamAuthorizationService;
        this.userStreamRoleRepository = userStreamRoleRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.emoteRepository = emoteRepository;
        this.emoteService = emoteService;
        this.metricsService = metricsService;
        this.autoModService = autoModService;
        this.chatMessagePersister = chatMessagePersister;
    }

    @Transactional
    public ChatMessageDTO sendMessage(String streamKey, String username,
                                      String content, MessageType messageType) {
        return sendMessage(streamKey, username, content, messageType, null);
    }

    @Transactional
    public ChatMessageDTO sendMessage(String streamKey, String username,
                                      String content, MessageType messageType,
                                      Long replyToMessageId) {
        return sendMessage(streamKey, username, content, messageType, replyToMessageId, null);
    }

    @Transactional
    public ChatMessageDTO sendMessage(String streamKey, String username,
                                      String content, MessageType messageType,
                                      Long replyToMessageId, String idempotencyKey) {
        long startTime = System.currentTimeMillis();

        try {
            // Idempotent publishing: if idempotencyKey exists, return existing message.
            // The idempotency_key index is GLOBAL (V7), so a key must belong to the
            // requested stream; reuse on another stream is a client bug.
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                var existing = chatMessageRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    Stream existingStream = streamRepository.findByStreamKey(streamKey)
                            .orElseThrow(() -> new RuntimeException("Stream not found"));
                    if (!existing.get().getStream().getId().equals(existingStream.getId())) {
                        throw new DataIntegrityViolationException(
                                "idempotencyKey already used on another stream");
                    }
                    return convertToDTO(existing.get());
                }
            }

            Stream stream = streamRepository.findByStreamKey(streamKey)
                    .orElseThrow(() -> new RuntimeException("Stream not found"));

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (moderationService.isUserBanned(stream.getId(), user.getId())) {
                metricsService.recordMessageRejected("banned");
                throw new UnauthorizedException("User is banned from this chat");
            }

            if (moderationService.isUserTimedOut(stream.getId(), user.getId())) {
                metricsService.recordMessageRejected("timed_out");
                throw new UnauthorizedException("User is timed out");
            }

            boolean privilegedUser = isPrivilegedUser(stream, user);

            enforceAccessModes(stream, user, content, privilegedUser);
            enforceSlowMode(stream, user, privilegedUser);

            // AutoMod analysis for non-privileged users
            if (!privilegedUser) {
                AutoModService.ModerationResult modResult = autoModService.analyzeMessage(stream, user, content);
                if (modResult.isBlocked()) {
                    metricsService.recordMessageRejected("automod");
                    throw new IllegalArgumentException(modResult.getReason());
                }
            }

            // Apply per-role rate limit overrides
            if (!checkRateLimitWithRoleOverride(stream, user, privilegedUser)) {
                metricsService.recordRateLimitExceeded();
                metricsService.recordMessageRejected("rate_limit");
                throw new RateLimitException("Rate limit exceeded. Please slow down.");
            }

            validateMessageContent(stream, content);

            ChatMessage message = ChatMessage.builder()
                    .stream(stream)
                    .user(user)
                    .username(username)
                    .content(content)
                    .replyToMessageId(replyToMessageId)
                    .messageType(messageType)
                    .idempotencyKey(idempotencyKey)
                    .build();

            ChatMessage saved;
            try {
                saved = chatMessagePersister.persist(message);
            } catch (DataIntegrityViolationException dup) {
                // Duplicate idempotency key: the INSERT ran in a REQUIRES_NEW
                // transaction (ChatMessagePersister), so the failed insert has
                // already been rolled back and this transaction was NOT marked
                // rollback-only. Recover by returning the winning row.
                if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                    throw dup;
                }
                ChatMessage existing = chatMessageRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> dup);
                if (!existing.getStream().getId().equals(stream.getId())) {
                    throw dup;
                }
                return convertToDTO(existing);
            }
            log.info("Message saved: id={}, stream={}, user={}",
                    saved.getId(), streamKey, username);

            if (redisTemplate != null) {
                try {
                    cacheRecentMessage(stream.getId(), saved);
                } catch (Exception e) {
                    log.warn("Failed to cache message in Redis: {}", e.getMessage());
                }
            }

            metricsService.recordMessageSent(streamKey, messageType.name());

            return convertToDTO(saved);
        } finally {
            metricsService.recordMessageProcessing(System.currentTimeMillis() - startTime);
        }
    }

    public List<ChatMessageDTO> getRecentMessages(String streamKey) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        if (redisTemplate != null) {
            try {
                String cacheKey = RECENT_MESSAGES_KEY + stream.getId();
                List<Object> cached = redisTemplate.opsForList().range(cacheKey, 0, -1);

                if (cached != null && !cached.isEmpty()) {
                    log.debug("Retrieved {} messages from cache for stream {}",
                            cached.size(), streamKey);
                    return cached.stream()
                            .map(obj -> (ChatMessageDTO) obj)
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve messages from Redis cache: {}", e.getMessage());
            }
        }

        log.debug("Loading messages from database for stream {}", streamKey);
        List<ChatMessage> messages = chatMessageRepository
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(stream.getId());

        Map<Long, ChatMessage> replyTargets = loadReplyTargets(messages);

        return messages.stream()
                .map(message -> convertToDTO(message, replyTargets))
                .collect(Collectors.toList());
    }

    public ChatHistoryResponse getMessageHistory(String streamKey, Long beforeMessageId, Integer limit) {
        return getMessageHistory(streamKey, beforeMessageId, limit, false);
    }

    public ChatHistoryResponse getMessageHistory(String streamKey, Long beforeMessageId, Integer limit, boolean includeDeleted) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        int pageSize = normalizeHistoryLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<ChatMessage> fetchedMessages;
        if (beforeMessageId == null) {
            fetchedMessages = includeDeleted
                    ? chatMessageRepository.findByStreamIdOrderByIdDesc(stream.getId(), pageRequest)
                    : chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(stream.getId(), pageRequest);
        } else {
            fetchedMessages = includeDeleted
                    ? chatMessageRepository.findByStreamIdAndIdLessThanOrderByIdDesc(stream.getId(), beforeMessageId, pageRequest)
                    : chatMessageRepository.findByStreamIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(
                            stream.getId(),
                            beforeMessageId,
                            pageRequest
                    );
        }

        boolean hasMore = fetchedMessages.size() > pageSize;
        List<ChatMessage> pageMessages = hasMore
                ? fetchedMessages.subList(0, pageSize)
                : fetchedMessages;

        Map<Long, ChatMessage> replyTargets = loadReplyTargets(pageMessages);

        List<ChatMessageDTO> messages = pageMessages.stream()
                .map(message -> convertToDTO(message, replyTargets))
                .collect(Collectors.toList());

        Long nextCursor = hasMore && !pageMessages.isEmpty()
                ? pageMessages.get(pageMessages.size() - 1).getId()
                : null;

        return ChatHistoryResponse.builder()
                .messages(messages)
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .build();
    }

    @Transactional
    public void deleteMessage(Long messageId, String deletedByUsername) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User deletedBy = userRepository.findByUsername(deletedByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!streamAuthorizationService.canModerate(
                message.getStream().getStreamKey(),
                deletedBy.getUsername())) {
            throw new UnauthorizedException("Insufficient permissions to delete message");
        }

        streamAuthorizationService.assertCanActOn(
                message.getStream().getStreamKey(),
                deletedBy,
                message.getUser(),
                true
        );

        message.setIsDeleted(true);
        message.setDeletedBy(deletedBy);
        message.setDeletedAt(LocalDateTime.now());

        chatMessageRepository.save(message);

        if (redisTemplate != null) {
            try {
                removeFromCache(message.getStream().getId(), messageId);
            } catch (Exception e) {
                log.warn("Failed to remove message from Redis cache: {}", e.getMessage());
            }
        }

        log.info("Message deleted: id={}, deletedBy={}", messageId, deletedByUsername);
    }

    @Transactional
    public int deleteMessagesByUser(Long streamId, Long userId, String deletedByUsername) {
        User deletedBy = userRepository.findByUsername(deletedByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Stream stream = streamRepository.findById(streamId).orElse(null);
        if (stream == null) {
            return 0;
        }

        if (!streamAuthorizationService.canModerate(stream.getStreamKey(), deletedBy.getUsername())) {
            throw new UnauthorizedException("Insufficient permissions to delete messages");
        }

        User targetUser = userRepository.findById(userId).orElse(null);
        if (targetUser == null) {
            return 0;
        }
        streamAuthorizationService.assertCanActOn(stream.getStreamKey(), deletedBy, targetUser, true);

        List<ChatMessage> messages = chatMessageRepository.findByStreamIdAndUserId(streamId, userId);
        int deletedCount = 0;

        for (ChatMessage message : messages) {
            if (Boolean.TRUE.equals(message.getIsDeleted())) {
                continue;
            }
            message.setIsDeleted(true);
            message.setDeletedBy(deletedBy);
            message.setDeletedAt(LocalDateTime.now());
            deletedCount++;
        }

        if (!messages.isEmpty()) {
            chatMessageRepository.saveAll(messages);
            if (redisTemplate != null) {
                try {
                    removeFromCache(streamId, null);
                } catch (Exception e) {
                    log.warn("Failed to remove message cache after bulk delete: {}", e.getMessage());
                }
            }
        }

        log.info("Deleted {} messages from user {} in stream {}", deletedCount, userId, streamId);
        return deletedCount;
    }

    private void cacheRecentMessage(Long streamId, ChatMessage message) {
        if (redisTemplate == null) return;

        String cacheKey = RECENT_MESSAGES_KEY + streamId;
        ChatMessageDTO dto = convertToDTO(message);

        redisTemplate.opsForList().leftPush(cacheKey, dto);
        redisTemplate.opsForList().trim(cacheKey, 0, RECENT_MESSAGES_LIMIT - 1);
        redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
    }

    private void removeFromCache(Long streamId, Long messageId) {
        if (redisTemplate == null) return;

        String cacheKey = RECENT_MESSAGES_KEY + streamId;
        redisTemplate.delete(cacheKey);
    }

    private void validateMessageContent(Stream stream, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        int maxLength = stream.getSettings() != null ?
                stream.getSettings().getMaxMessageLength() : 500;

        if (content.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Message exceeds maximum length of " + maxLength + " characters");
        }

        if (stream.getSettings() != null &&
                stream.getSettings().getProfanityFilterEnabled()) {
            if (moderationService.containsProfanity(content)) {
                metricsService.recordBannedWordDetected();
                metricsService.recordMessageRejected("profanity");
                throw new IllegalArgumentException("Message contains blocked words");
            }
        }

        if (stream.getSettings() != null &&
                stream.getSettings().getLinkProtectionEnabled()) {
            if (containsLinks(content)) {
                metricsService.recordMessageRejected("links");
                throw new IllegalArgumentException("Links are not allowed in this chat");
            }
        }
    }

    private void enforceAccessModes(Stream stream, User user, String content, boolean privilegedUser) {
        if (privilegedUser || stream.getSettings() == null) {
            return;
        }

        if (Boolean.TRUE.equals(stream.getSettings().getSubscribersOnlyMode()) &&
                !userBadgeRepository.hasBadge(user.getId(), stream.getId(), SUBSCRIBER_BADGE)) {
            throw new UnauthorizedException("Chat is in subscribers-only mode");
        }

        if (Boolean.TRUE.equals(stream.getSettings().getFollowersOnlyMode()) &&
                !isEligibleFollower(stream, user)) {
            throw new UnauthorizedException("Chat is in followers-only mode");
        }

        if (Boolean.TRUE.equals(stream.getSettings().getEmoteOnlyMode()) &&
                !containsOnlyEmotes(stream.getId(), content)) {
            throw new IllegalArgumentException("Chat is in emote-only mode");
        }
    }

    private boolean isEligibleFollower(Stream stream, User user) {
        Integer requiredMinutes = stream.getSettings().getFollowersOnlyDurationMinutes();
        if (requiredMinutes != null && requiredMinutes > 0) {
            LocalDateTime grantedBefore = LocalDateTime.now().minusMinutes(requiredMinutes);
            return userBadgeRepository.hasBadgeGrantedBefore(
                    user.getId(),
                    stream.getId(),
                    FOLLOWER_BADGE,
                    grantedBefore
            );
        }

        return userBadgeRepository.hasBadge(user.getId(), stream.getId(), FOLLOWER_BADGE);
    }

    private boolean containsOnlyEmotes(Long streamId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            String normalized = normalizeEmoteToken(token);
            if (normalized.isEmpty() || !emoteRepository.existsByStreamIdAndCode(streamId, normalized)) {
                return false;
            }
        }

        return true;
    }

    private String normalizeEmoteToken(String token) {
        return token == null ? "" : token.replaceAll("^:+|:+$", "");
    }

    private void enforceSlowMode(Stream stream, User user, boolean privilegedUser) {
        if (privilegedUser || stream.getSettings() == null ||
                !Boolean.TRUE.equals(stream.getSettings().getSlowModeEnabled())) {
            return;
        }

        Integer slowModeSeconds = stream.getSettings().getSlowModeSeconds();
        if (slowModeSeconds == null || slowModeSeconds <= 0) {
            return;
        }

        String key = slowModeKey(stream.getId(), user.getId());
        long now = System.currentTimeMillis();
        long waitMillis = checkSlowMode(key, now, slowModeSeconds);
        if (waitMillis > 0) {
            long waitSeconds = (waitMillis + 999L) / 1000L;
            throw new RateLimitException("Slow mode is enabled. Wait " + waitSeconds + " more seconds.");
        }
    }

    private long checkSlowMode(String key, long now, int slowModeSeconds) {
        if ("memory".equalsIgnoreCase(slowModeStorage)) {
            return checkSlowModeMemory(key, now, slowModeSeconds);
        }
        return checkSlowModeRedis(key, now, slowModeSeconds);
    }

    /**
     * Redis-backed slow mode enforcement: a single atomic Lua GET+SET with
     * TTL = slowModeSeconds + 5. Cross-instance safe. Fails OPEN: on any
     * Redis error the message is allowed and chat.slowmode.redis_fallback
     * is incremented (slow mode is a QoL feature with no DB backing).
     */
    private long checkSlowModeRedis(String key, long now, int slowModeSeconds) {
        if (redisTemplate == null) {
            return 0;
        }
        try {
            Long waitMillis = redisTemplate.execute((RedisCallback<Long>) connection -> {
                Object result = connection.eval(SLOW_MODE_SCRIPT_BYTES, ReturnType.INTEGER, 1,
                        key.getBytes(StandardCharsets.UTF_8),
                        Long.toString(now).getBytes(StandardCharsets.UTF_8),
                        Long.toString((long) slowModeSeconds * 1000L).getBytes(StandardCharsets.UTF_8),
                        Long.toString((long) slowModeSeconds + 5L).getBytes(StandardCharsets.UTF_8));
                return result instanceof Number number ? number.longValue() : 0L;
            });
            return waitMillis == null ? 0 : waitMillis;
        } catch (Exception e) {
            metricsService.recordSlowModeRedisFallback();
            log.warn("Failed to enforce slow mode via Redis, allowing message: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * In-memory slow mode store (dev escape hatch when chat.slowmode.storage
     * is "memory"). Mirrors the Redis semantics: single node only.
     */
    private long checkSlowModeMemory(String key, long now, int slowModeSeconds) {
        long slowModeMillis = (long) slowModeSeconds * 1000L;
        SlowModeEntry entry = slowModeMemoryCache.getIfPresent(key);
        if (entry == null || now - entry.lastAtMs() >= slowModeMillis) {
            slowModeMemoryCache.put(key, new SlowModeEntry(now, (long) slowModeSeconds + 5L));
            return 0;
        }
        return slowModeMillis - (now - entry.lastAtMs());
    }

    private String slowModeKey(Long streamId, Long userId) {
        return SLOW_MODE_KEY + streamId + ":" + userId;
    }

    private boolean isPrivilegedUser(Stream stream, User user) {
        return stream.getUser().getId().equals(user.getId()) ||
                streamAuthorizationService.canModerate(stream.getStreamKey(), user.getUsername());
    }

    private boolean containsLinks(String content) {
        return content.matches(".*https?://.*");
    }

    private int normalizeHistoryLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_HISTORY_LIMIT;
        }

        if (requestedLimit < 1) {
            return 1;
        }

        return Math.min(requestedLimit, MAX_HISTORY_LIMIT);
    }

    /**
     * Check rate limit with per-role overrides.
     * Different roles have different limits for handling spam.
     */
    private boolean checkRateLimitWithRoleOverride(Stream stream, User user, boolean privilegedUser) {
        // Privileged users (broadcaster, moderator) have higher limits
        if (privilegedUser) {
            // Broadcasters and moderators: 100 messages per minute
            return rateLimitService.allowMessage(stream.getId(), user.getId(), 100, 60);
        }

        // Check for subscriber badge
        boolean isSubscriber = userBadgeRepository.hasBadge(user.getId(), stream.getId(), SUBSCRIBER_BADGE);

        if (isSubscriber) {
            // Subscribers: 50 messages per minute
            return rateLimitService.allowMessage(stream.getId(), user.getId(), 50, 60);
        }

        // Regular users: default rate limit (20 messages per 60 seconds)
        return rateLimitService.allowMessage(stream.getId(), user.getId());
    }

    private ChatMessageDTO convertToDTO(ChatMessage message) {
        Map<Long, ChatMessage> replyTargets = Map.of();
        if (message.getReplyToMessageId() != null) {
            Optional<ChatMessage> target = chatMessageRepository
                    .findById(message.getReplyToMessageId());
            if (target.isPresent()) {
                replyTargets = Map.of(target.get().getId(), target.get());
            }
        }
        return convertToDTO(message, replyTargets);
    }

    /**
     * Convert a message to its DTO, resolving the reply preview from a
     * preloaded map of reply targets. List paths preload the map in a single
     * batch query ({@link #loadReplyTargets}) so preview resolution never
     * triggers per-message lookups.
     */
    private ChatMessageDTO convertToDTO(ChatMessage message,
                                        Map<Long, ChatMessage> replyTargets) {
        String content = message.getIsDeleted() != null && message.getIsDeleted()
                 ? "Message deleted"
                : message.getContent();

        Map<String, Emote> emoteMap = streamEmoteCache.getIfPresent(
                String.valueOf(message.getStream().getId()));
if (emoteMap == null) {
    emoteMap = emoteService.getStreamEmotes(message.getStream().getId());
    streamEmoteCache.put(String.valueOf(message.getStream().getId()), emoteMap);
}

List<MessageFragmentDTO> fragments = new ArrayList<>();
// Reuse the same parsing logic as buildMessageFragments but with cached emoteMap
String content = message.getContent();
if (content != null && !content.isEmpty()) {
    Pattern pattern = Pattern.compile("(:[A-Za-z0-9_]+:)");
    Matcher matcher = pattern.matcher(content);
    int lastIndex = 0;
    while (matcher.find()) {
        if (matcher.start() > lastIndex) {
            fragments.add(MessageFragmentDTO.builder()
                    .type(MessageFragmentType.TEXT)
                    .text(content.substring(lastIndex, matcher.start()))
                    .build());
        }
        String token = matcher.group(1);
        String code = normalizeEmoteToken(token);
        Emote emote = emoteMap.get(code);
        if (emote != null) {
            fragments.add(MessageFragmentDTO.builder()
                    .type(MessageFragmentType.EMOTE)
                    .text(token)
                    .emoteCode(code)
                    .imageUrl(emote.getImageUrl())
                    .build());
        } else {
            fragments.add(MessageFragmentDTO.builder()
                    .type(MessageFragmentType.TEXT)
                    .text(token)
                    .build());
        }
        lastIndex = matcher.end();
    }
    if (lastIndex < content.length()) {
        fragments.add(MessageFragmentDTO.builder()
                .type(MessageFragmentType.TEXT)
                .text(content.substring(lastIndex))
                .build());
    }
}

        ChatMessageDTO.ChatMessageDTOBuilder dtoBuilder = ChatMessageDTO.builder()
                .id(message.getId())
                .streamId(message.getStream().getId())
                .userId(message.getUser().getId())
                .username(message.getUsername())
                .content(content)
                .replyToMessageId(message.getReplyToMessageId())
                .messageType(message.getIsDeleted() != null && message.getIsDeleted() ? MessageType.DELETED : message.getMessageType())
                .color(message.getUser().getColor())
                .badges(getUserBadges(message.getUser(), message.getStream()))
                .fragments(fragments)
                .isDeleted(Boolean.TRUE.equals(message.getIsDeleted()))
                .deletedById(message.getDeletedBy() != null ? message.getDeletedBy().getId() : null)
                .deletedByUsername(message.getDeletedBy() != null ? message.getDeletedBy().getUsername() : null)
                .deletedAt(message.getDeletedAt())
                .isPinned(Boolean.TRUE.equals(message.getIsPinned()))
                .pinnedAt(message.getPinnedAt())
                .pinnedByUsername(message.getPinnedBy() != null ? message.getPinnedBy().getUsername() : null)
                .idempotencyKey(message.getIdempotencyKey())
                .redisSequenceId(message.getRedisSequenceId())
                .timestamp(message.getCreatedAt());

        if (message.getReplyToMessageId() != null) {
            applyReplyPreview(dtoBuilder, replyTargets.get(message.getReplyToMessageId()));
        }

        return dtoBuilder.build();
    }

    /**
     * Collect the reply targets referenced by a list of messages in a single
     * batch query. An empty reply set issues no query at all.
     */
    private Map<Long, ChatMessage> loadReplyTargets(List<ChatMessage> messages) {
        Set<Long> replyIds = messages.stream()
                .map(ChatMessage::getReplyToMessageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (replyIds.isEmpty()) {
            return Map.of();
        }

        return chatMessageRepository.findAllById(replyIds).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
    }

    /**
     * Shared reply-preview builder used by both the single-message and the
     * batch DTO paths so the deleted-target rule cannot drift between them.
     * A missing target (orphaned replyToMessageId) produces no preview.
     */
    private void applyReplyPreview(ChatMessageDTO.ChatMessageDTOBuilder dtoBuilder,
                                   ChatMessage target) {
        if (target == null) {
            return;
        }
        dtoBuilder.replyToUsername(target.getUsername());
        String preview = target.getIsDeleted() != null && target.getIsDeleted()
                ? "Message deleted"
                : target.getContent();
        if (preview != null && preview.length() > 100) {
            preview = preview.substring(0, 100) + "...";
        }
        dtoBuilder.replyToContentPreview(preview);
    }

    private List<String> getUserBadges(User user, Stream stream) {
        BadgeCacheKey key = new BadgeCacheKey(user.getId(), stream.getId());
        List<String> cached = userBadgeCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        Set<Long> userIds = Set.of(user.getId());
        Set<Long> streamIds = Set.of(stream.getId());

        List<String> badgeTypes = userBadgeRepository.findBadgeTypesByUserIdAndStreamIdOrGlobalIn(userIds, streamIds);

        List<UserStreamRole> streamRoles = userStreamRoleRepository.findByUserIdAndStreamIdIn(user.getId(), streamIds);

        Set<String> badges = new LinkedHashSet<>(badgeTypes);
        if (stream.getUser().getId().equals(user.getId())) {
            badges.add(com.streamchat.model.enums.UserBadge.BROADCASTER.name());
        }
        if (streamRoles.stream().map(UserStreamRole::getRole).anyMatch(role -> role == Role.ROLE_MODERATOR)) {
            badges.add(com.streamchat.model.enums.UserBadge.MODERATOR.name());
        }

        List<String> result = new ArrayList<>(badges);
        userBadgeCache.put(key, result);
        return result;
    }
}
