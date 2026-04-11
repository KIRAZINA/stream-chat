package com.streamchat.service;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final int RECENT_MESSAGES_LIMIT = 100;
    private static final String RECENT_MESSAGES_KEY = "recent:messages:";
    private static final String SLOW_MODE_KEY = "slowmode:lastmessage:";
    private static final String FOLLOWER_BADGE = com.streamchat.model.enums.UserBadge.FOLLOWER.name();
    private static final String SUBSCRIBER_BADGE = com.streamchat.model.enums.UserBadge.SUBSCRIBER.name();
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final Map<String, Long> inMemorySlowModeState = new ConcurrentHashMap<>();

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
                       AutoModService autoModService) {
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
        long startTime = System.currentTimeMillis();

        try {
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
                    .build();

            ChatMessage saved = chatMessageRepository.save(message);
            log.info("Message saved: id={}, stream={}, user={}",
                    saved.getId(), streamKey, username);

            if (redisTemplate != null) {
                try {
                    cacheRecentMessage(stream.getId(), saved);
                } catch (Exception e) {
                    log.warn("Failed to cache message in Redis: {}", e.getMessage());
                }
            }

            rememberSlowModeActivity(stream, user, privilegedUser);

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

        return messages.stream()
                .map(this::convertToDTO)
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

        List<ChatMessageDTO> messages = pageMessages.stream()
                .map(this::convertToDTO)
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
        Long lastMessageAt = readSlowModeTimestamp(key);
        if (lastMessageAt == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long waitMillis = (slowModeSeconds * 1000L) - (now - lastMessageAt);
        if (waitMillis > 0) {
            long waitSeconds = (waitMillis + 999L) / 1000L;
            throw new RateLimitException("Slow mode is enabled. Wait " + waitSeconds + " more seconds.");
        }
    }

    private void rememberSlowModeActivity(Stream stream, User user, boolean privilegedUser) {
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
        inMemorySlowModeState.put(key, now);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, Long.toString(now), slowModeSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Failed to persist slow mode state in Redis: {}", e.getMessage());
            }
        }
    }

    private Long readSlowModeTimestamp(String key) {
        if (redisTemplate != null) {
            try {
                Object rawValue = redisTemplate.opsForValue().get(key);
                if (rawValue != null) {
                    return Long.parseLong(rawValue.toString());
                }
            } catch (Exception e) {
                log.warn("Failed to read slow mode state from Redis: {}", e.getMessage());
            }
        }

        return inMemorySlowModeState.get(key);
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
        String content = message.getIsDeleted() != null && message.getIsDeleted()
                ? "Сообщение удалено"
                : message.getContent();

        List<MessageFragmentDTO> fragments = message.getIsDeleted() != null && message.getIsDeleted()
                ? List.of()
                : emoteService.buildMessageFragments(message.getStream().getId(), message.getContent());

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
                .timestamp(message.getCreatedAt());

        if (message.getReplyToMessageId() != null) {
            chatMessageRepository.findById(message.getReplyToMessageId()).ifPresent(replyMessage -> {
                dtoBuilder.replyToUsername(replyMessage.getUsername());
                String preview = replyMessage.getContent();
                if (preview != null && preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }
                dtoBuilder.replyToContentPreview(preview);
            });
        }

        return dtoBuilder.build();
    }

    private List<String> getUserBadges(User user, Stream stream) {
        Set<String> badges = new LinkedHashSet<>(userBadgeRepository
                .findBadgeTypesByUserIdAndStreamIdOrGlobal(user.getId(), stream.getId()));

        if (stream.getUser().getId().equals(user.getId())) {
            badges.add(com.streamchat.model.enums.UserBadge.BROADCASTER.name());
        }

        List<UserStreamRole> streamRoles = userStreamRoleRepository
                .findByUserIdAndStreamId(user.getId(), stream.getId());
        if (streamRoles.stream().map(UserStreamRole::getRole).anyMatch(role -> role == Role.ROLE_MODERATOR)) {
            badges.add(com.streamchat.model.enums.UserBadge.MODERATOR.name());
        }

        return new ArrayList<>(badges);
    }
}
