package com.streamchat.service;

import com.streamchat.exception.RateLimitException;
import com.streamchat.exception.UnauthorizedException;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.entity.ChatMessage;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

    // Optional Redis dependencies - only available when Redis is configured
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private RedisMessagePublisher redisMessagePublisher;

    private static final int RECENT_MESSAGES_LIMIT = 100;
    private static final String RECENT_MESSAGES_KEY = "recent:messages:";

    /**
     * Constructor with required dependencies only.
     * Redis dependencies are injected optionally via field injection.
     */
    public ChatService(ChatMessageRepository chatMessageRepository,
                       StreamRepository streamRepository,
                       UserRepository userRepository,
                       RateLimitService rateLimitService,
                       ModerationService moderationService) {
        this.chatMessageRepository = chatMessageRepository;
        this.streamRepository = streamRepository;
        this.userRepository = userRepository;
        this.rateLimitService = rateLimitService;
        this.moderationService = moderationService;
    }

    /**
     * Process and save a new chat message.
     * Performs validation, rate limiting, moderation checks, and caching.
     *
     * @param streamKey the stream identifier
     * @param username the sender's username
     * @param content the message content
     * @param messageType the type of message
     * @return the saved message DTO
     */
    @Transactional
    public ChatMessageDTO sendMessage(String streamKey, String username,
                                      String content, MessageType messageType) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (moderationService.isUserBanned(stream.getId(), user.getId())) {
            throw new UnauthorizedException("User is banned from this chat");
        }

        if (moderationService.isUserTimedOut(stream.getId(), user.getId())) {
            throw new UnauthorizedException("User is timed out");
        }

        if (!rateLimitService.allowMessage(stream.getId(), user.getId())) {
            throw new RateLimitException("Rate limit exceeded. Please slow down.");
        }

        validateMessageContent(stream, content);

        ChatMessage message = ChatMessage.builder()
                .stream(stream)
                .user(user)
                .username(username)
                .content(content)
                .messageType(messageType)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Message saved: id={}, stream={}, user={}",
                saved.getId(), streamKey, username);

        // Cache only if Redis is available
        if (redisTemplate != null) {
            try {
                cacheRecentMessage(stream.getId(), saved);
            } catch (Exception e) {
                log.warn("Failed to cache message in Redis: {}", e.getMessage());
            }
        }

        ChatMessageDTO dto = convertToDTO(saved);

        // Publish only if Redis is available
        if (redisMessagePublisher != null) {
            try {
                redisMessagePublisher.publish(streamKey, dto);
            } catch (Exception e) {
                log.warn("Failed to publish message to Redis: {}", e.getMessage());
            }
        }

        return dto;
    }

    /**
     * Retrieve recent messages for a stream from cache or database.
     *
     * @param streamKey the stream identifier
     * @return list of recent messages
     */
    public List<ChatMessageDTO> getRecentMessages(String streamKey) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        // Try cache only if Redis is available
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

        // Fallback to database
        log.debug("Loading messages from database for stream {}", streamKey);
        List<ChatMessage> messages = chatMessageRepository
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(stream.getId());

        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Delete a message (soft delete).
     *
     * @param messageId the message ID
     * @param deletedByUsername the moderator's username
     */
    @Transactional
    public void deleteMessage(Long messageId, String deletedByUsername) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User deletedBy = userRepository.findByUsername(deletedByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!moderationService.canModerate(message.getStream().getId(), deletedBy.getId())) {
            throw new UnauthorizedException("Insufficient permissions to delete message");
        }

        message.setIsDeleted(true);
        message.setDeletedBy(deletedBy);
        message.setDeletedAt(LocalDateTime.now());

        chatMessageRepository.save(message);

        // Remove from cache only if Redis is available
        if (redisTemplate != null) {
            try {
                removeFromCache(message.getStream().getId(), messageId);
            } catch (Exception e) {
                log.warn("Failed to remove message from Redis cache: {}", e.getMessage());
            }
        }

        log.info("Message deleted: id={}, deletedBy={}", messageId, deletedByUsername);
    }

    /**
     * Cache a message in Redis for quick retrieval.
     */
    private void cacheRecentMessage(Long streamId, ChatMessage message) {
        if (redisTemplate == null) return;

        String cacheKey = RECENT_MESSAGES_KEY + streamId;
        ChatMessageDTO dto = convertToDTO(message);

        redisTemplate.opsForList().leftPush(cacheKey, dto);
        redisTemplate.opsForList().trim(cacheKey, 0, RECENT_MESSAGES_LIMIT - 1);
        redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
    }

    /**
     * Remove a message from cache.
     */
    private void removeFromCache(Long streamId, Long messageId) {
        if (redisTemplate == null) return;

        String cacheKey = RECENT_MESSAGES_KEY + streamId;
        redisTemplate.delete(cacheKey);
    }

    /**
     * Validate message content against stream settings.
     */
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
                throw new IllegalArgumentException("Message contains blocked words");
            }
        }

        if (stream.getSettings() != null &&
                stream.getSettings().getLinkProtectionEnabled()) {
            if (containsLinks(content)) {
                throw new IllegalArgumentException("Links are not allowed in this chat");
            }
        }
    }

    /**
     * Check if content contains URLs.
     */
    private boolean containsLinks(String content) {
        return content.matches(".*https?://.*");
    }

    /**
     * Convert entity to DTO.
     */
    private ChatMessageDTO convertToDTO(ChatMessage message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .streamId(message.getStream().getId())
                .userId(message.getUser().getId())
                .username(message.getUsername())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .color(message.getUser().getColor())
                .badges(getUserBadges(message.getUser(), message.getStream()))
                .timestamp(message.getCreatedAt())
                .build();
    }

    /**
     * Get user's badges for a specific stream.
     */
    private List<String> getUserBadges(User user, Stream stream) {
        // Implementation depends on badge logic
        return List.of();
    }
}