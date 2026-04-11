package com.streamchat.service;

import com.streamchat.model.entity.*;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling chat moderation operations.
 * Manages user timeouts, bans, and content filtering.
 */
@Service
@Slf4j
public class ModerationService {

    private final BannedUserRepository bannedUserRepository;
    private final TimedOutUserRepository timedOutUserRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final UserStreamRoleRepository userStreamRoleRepository;
    private final BlockedWordRepository blockedWordRepository;
    private final MetricsService metricsService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final String BAN_CACHE_KEY = "ban:";
    private static final String TIMEOUT_CACHE_KEY = "timeout:";

    public ModerationService(BannedUserRepository bannedUserRepository,
                             TimedOutUserRepository timedOutUserRepository,
                             ModerationLogRepository moderationLogRepository,
                             UserStreamRoleRepository userStreamRoleRepository,
                             BlockedWordRepository blockedWordRepository,
                             MetricsService metricsService) {
        this.bannedUserRepository = bannedUserRepository;
        this.timedOutUserRepository = timedOutUserRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.userStreamRoleRepository = userStreamRoleRepository;
        this.blockedWordRepository = blockedWordRepository;
        this.metricsService = metricsService;
    }

    @Transactional
    public void timeoutUser(Long streamId, Long userId, Long moderatorId,
                            int durationSeconds, String reason) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Timeout duration must be positive");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(durationSeconds);

        TimedOutUser timeout = TimedOutUser.builder()
                .streamId(streamId)
                .userId(userId)
                .timedOutById(moderatorId)
                .durationSeconds(durationSeconds)
                .expiresAt(expiresAt)
                .reason(reason)
                .build();

        timedOutUserRepository.save(timeout);

        // Cache in Redis if available
        if (redisTemplate != null) {
            try {
                String cacheKey = TIMEOUT_CACHE_KEY + streamId + ":" + userId;
                redisTemplate.opsForValue().set(cacheKey, "1", durationSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Failed to cache timeout in Redis: {}", e.getMessage());
            }
        }

        logModerationAction(streamId, moderatorId, userId,
                ModerationActionType.TIMEOUT, reason, durationSeconds);

        metricsService.recordModerationAction("timeout");

        log.info("User timed out: streamId={}, userId={}, duration={}s",
                streamId, userId, durationSeconds);
    }

    @Transactional
    public void banUser(Long streamId, Long userId, Long moderatorId,
                        boolean isPermanent, Integer durationSeconds, String reason) {
        if (!isPermanent && (durationSeconds == null || durationSeconds <= 0)) {
            throw new IllegalArgumentException("Ban duration must be positive for temporary bans");
        }
        LocalDateTime expiresAt = isPermanent ? null :
                LocalDateTime.now().plusSeconds(durationSeconds);

        BannedUser ban = BannedUser.builder()
                .streamId(streamId)
                .userId(userId)
                .bannedById(moderatorId)
                .isPermanent(isPermanent)
                .expiresAt(expiresAt)
                .reason(reason)
                .build();

        bannedUserRepository.save(ban);

        // Cache in Redis if available
        if (redisTemplate != null) {
            try {
                String cacheKey = BAN_CACHE_KEY + streamId + ":" + userId;
                if (isPermanent) {
                    redisTemplate.opsForValue().set(cacheKey, "1");
                } else {
                    redisTemplate.opsForValue().set(cacheKey, "1",
                            durationSeconds, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.warn("Failed to cache ban in Redis: {}", e.getMessage());
            }
        }

        logModerationAction(streamId, moderatorId, userId,
                ModerationActionType.BAN, reason, durationSeconds);

        metricsService.recordModerationAction("ban");

        log.info("User banned: streamId={}, userId={}, permanent={}",
                streamId, userId, isPermanent);
    }

    @Transactional
    public void unbanUser(Long streamId, Long userId, Long moderatorId) {
        bannedUserRepository.deleteByStreamIdAndUserId(streamId, userId);

        metricsService.recordModerationAction("unban");

        // Remove from cache if Redis is available
        if (redisTemplate != null) {
            try {
                String cacheKey = BAN_CACHE_KEY + streamId + ":" + userId;
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to remove ban from Redis cache: {}", e.getMessage());
            }
        }

        logModerationAction(streamId, moderatorId, userId,
                ModerationActionType.UNBAN, null, null);

        log.info("User unbanned: streamId={}, userId={}", streamId, userId);
    }

    public boolean isUserBanned(Long streamId, Long userId) {
        // Check cache first if Redis is available
        if (redisTemplate != null) {
            try {
                String cacheKey = BAN_CACHE_KEY + streamId + ":" + userId;
                if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to check ban in Redis cache: {}", e.getMessage());
            }
        }

        // Check database
        return bannedUserRepository.existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId);
    }

    public boolean isUserTimedOut(Long streamId, Long userId) {
        // Check cache first if Redis is available
        if (redisTemplate != null) {
            try {
                String cacheKey = TIMEOUT_CACHE_KEY + streamId + ":" + userId;
                if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to check timeout in Redis cache: {}", e.getMessage());
            }
        }

        // Check database
        return timedOutUserRepository.existsByStreamIdAndUserIdAndActiveTimeout(
                streamId, userId, LocalDateTime.now());
    }

    public boolean canModerate(Long streamId, Long userId) {
        return userStreamRoleRepository.hasModeratorRole(streamId, userId);
    }

    public boolean containsProfanity(String content) {
        String lowerContent = content.toLowerCase();

        List<BlockedWord> blockedWords = blockedWordRepository.findAllGlobal();

        for (BlockedWord word : blockedWords) {
            if (word.getIsRegex()) {
                if (java.util.regex.Pattern.compile(word.getWord(),
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(content)
                        .find()) {
                    return true;
                }
            } else {
                if (lowerContent.contains(word.getWord().toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }

    private void logModerationAction(Long streamId, Long moderatorId, Long targetUserId,
                                     ModerationActionType actionType, String reason,
                                     Integer durationSeconds) {
        LocalDateTime expiresAt = durationSeconds != null ?
                LocalDateTime.now().plusSeconds(durationSeconds) : null;

        ModerationLog log = ModerationLog.builder()
                .streamId(streamId)
                .moderatorId(moderatorId)
                .targetUserId(targetUserId)
                .actionType(actionType)
                .reason(reason)
                .durationSeconds(durationSeconds)
                .expiresAt(expiresAt)
                .build();

        moderationLogRepository.save(log);
    }
}
