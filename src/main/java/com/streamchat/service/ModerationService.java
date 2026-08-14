package com.streamchat.service;

import com.streamchat.exception.ConflictException;
import com.streamchat.model.entity.*;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamAuthorizationService streamAuthorizationService;
    private final ModerationPersister moderationPersister;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final String BAN_CACHE_KEY = "ban:";
    private static final String TIMEOUT_CACHE_KEY = "timeout:";

    public ModerationService(BannedUserRepository bannedUserRepository,
                             TimedOutUserRepository timedOutUserRepository,
                             ModerationLogRepository moderationLogRepository,
                             UserStreamRoleRepository userStreamRoleRepository,
                             BlockedWordRepository blockedWordRepository,
                             MetricsService metricsService,
                             RefreshTokenRepository refreshTokenRepository,
                             UserRepository userRepository,
                             SimpMessagingTemplate messagingTemplate,
                             StreamAuthorizationService streamAuthorizationService,
                             ModerationPersister moderationPersister) {
        this.bannedUserRepository = bannedUserRepository;
        this.timedOutUserRepository = timedOutUserRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.userStreamRoleRepository = userStreamRoleRepository;
        this.blockedWordRepository = blockedWordRepository;
        this.metricsService = metricsService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.streamAuthorizationService = streamAuthorizationService;
        this.moderationPersister = moderationPersister;
    }

    @Transactional
    public void timeoutUser(Long streamId, Long userId, Long moderatorId,
                            int durationSeconds, String reason) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Timeout duration must be positive");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(durationSeconds);

        // Idempotent upsert: extend an existing active timeout, otherwise insert.
        TimedOutUser existing = timedOutUserRepository
                .findActiveTimeout(streamId, userId, LocalDateTime.now())
                .orElse(null);
        if (existing != null) {
            applyTimeoutFields(existing, moderatorId, durationSeconds, expiresAt, reason);
            updateTimeoutWithRetry(existing, streamId, userId, moderatorId,
                    durationSeconds, expiresAt, reason);
        } else {
            TimedOutUser timeout = TimedOutUser.builder()
                    .streamId(streamId)
                    .userId(userId)
                    .timedOutById(moderatorId)
                    .durationSeconds(durationSeconds)
                    .expiresAt(expiresAt)
                    .reason(reason)
                    .build();
            try {
                moderationPersister.insertTimeout(timeout);
            } catch (DataIntegrityViolationException dup) {
                // timed_out_users has no UNIQUE constraint today, so this is only
                // reachable if one is added later. The failed insert ran in its
                // own REQUIRES_NEW transaction and was rolled back, so this
                // transaction is still usable: re-fetch the active winner and
                // treat the call as concurrent success.
                timedOutUserRepository.findActiveTimeout(streamId, userId, LocalDateTime.now())
                        .orElseThrow(() -> dup);
            }
        }

        // Cache in Redis if available
        if (redisTemplate != null) {
            try {
                String cacheKey = TIMEOUT_CACHE_KEY + streamId + ":" + userId;
                redisTemplate.opsForValue().set(cacheKey, "1", durationSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Failed to cache timeout in Redis: {}", e.getMessage());
            }
        }

        streamAuthorizationService.evictRoleCache(streamId, userId);

        logModerationAction(streamId, moderatorId, userId,
                ModerationActionType.TIMEOUT, reason, durationSeconds);

        metricsService.recordModerationAction("timeout");

        forceDisconnect(userId);

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

        // Idempotent upsert: update an existing active ban, otherwise insert.
        BannedUser existing = bannedUserRepository
                .findActiveBanByStreamAndUser(streamId, userId)
                .orElse(null);
        if (existing != null) {
            applyBanFields(existing, moderatorId, isPermanent, expiresAt, reason);
            updateBanWithRetry(existing, streamId, userId, moderatorId,
                    isPermanent, expiresAt, reason);
        } else {
            BannedUser ban = BannedUser.builder()
                    .streamId(streamId)
                    .userId(userId)
                    .bannedById(moderatorId)
                    .isPermanent(isPermanent)
                    .expiresAt(expiresAt)
                    .reason(reason)
                    .build();
            try {
                moderationPersister.insertBan(ban);
            } catch (DataIntegrityViolationException dup) {
                // Race: another thread just inserted the record for (stream, user).
                // The failed insert ran in its own REQUIRES_NEW transaction and
                // was rolled back, so this transaction is still usable: re-fetch
                // the winning row and treat the call as concurrent success.
                bannedUserRepository.findActiveBanByStreamAndUser(streamId, userId)
                        .orElseThrow(() -> dup);
            }
        }

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

        streamAuthorizationService.evictRoleCache(streamId, userId);

        logModerationAction(streamId, moderatorId, userId,
                ModerationActionType.BAN, reason, durationSeconds);

        metricsService.recordModerationAction("ban");

        forceDisconnect(userId);

        log.info("User banned: streamId={}, userId={}, permanent={}",
                streamId, userId, isPermanent);
    }

    @Transactional
    public void unbanUser(Long streamId, Long userId, Long moderatorId) {
        BannedUser ban = bannedUserRepository
                .findActiveBanByStreamAndUser(streamId, userId)
                .orElse(null);
        if (ban == null) {
            // Idempotent: no active ban to remove, silent success.
            log.info("No active ban to remove: streamId={}, userId={}", streamId, userId);
            return;
        }

        // Deactivate instead of deleting: keeps the moderation history row and
        // plays nicely with the (stream_id, user_id) unique constraint.
        ban.setIsPermanent(false);
        ban.setExpiresAt(LocalDateTime.now());
        try {
            bannedUserRepository.saveAndFlush(ban);
        } catch (ObjectOptimisticLockingFailureException ex) {
            BannedUser refreshed = bannedUserRepository
                    .findActiveBanByStreamAndUser(streamId, userId)
                    .orElseThrow(() -> new ConflictException(
                            "Ban state changed concurrently; please retry"));
            refreshed.setIsPermanent(false);
            refreshed.setExpiresAt(LocalDateTime.now());
            try {
                bannedUserRepository.saveAndFlush(refreshed);
            } catch (ObjectOptimisticLockingFailureException retryEx) {
                throw new ConflictException("Ban state changed concurrently; please retry");
            }
        }

        // Remove from cache if Redis is available
        if (redisTemplate != null) {
            try {
                String cacheKey = BAN_CACHE_KEY + streamId + ":" + userId;
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to remove ban from Redis cache: {}", e.getMessage());
            }
        }

        streamAuthorizationService.evictRoleCache(streamId, userId);

        logModerationAction(streamId, moderatorId, userId,
                ModerationActionType.UNBAN, null, null);

        metricsService.recordModerationAction("unban");

        log.info("User unbanned: streamId={}, userId={}", streamId, userId);
    }

    private void applyBanFields(BannedUser ban, Long moderatorId, boolean isPermanent,
                                LocalDateTime expiresAt, String reason) {
        ban.setBannedById(moderatorId);
        ban.setIsPermanent(isPermanent);
        ban.setExpiresAt(expiresAt);
        ban.setReason(reason);
    }

    private void applyTimeoutFields(TimedOutUser timeout, Long moderatorId, int durationSeconds,
                                    LocalDateTime expiresAt, String reason) {
        timeout.setTimedOutById(moderatorId);
        timeout.setDurationSeconds(durationSeconds);
        timeout.setExpiresAt(expiresAt);
        timeout.setReason(reason);
    }

    private void updateBanWithRetry(BannedUser existing, Long streamId, Long userId, Long moderatorId,
                                    boolean isPermanent, LocalDateTime expiresAt, String reason) {
        try {
            bannedUserRepository.saveAndFlush(existing);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Optimistic-lock conflict: retry once against the freshest state.
            BannedUser refreshed = bannedUserRepository
                    .findActiveBanByStreamAndUser(streamId, userId)
                    .orElseThrow(() -> new ConflictException(
                            "Ban state changed concurrently; please retry"));
            applyBanFields(refreshed, moderatorId, isPermanent, expiresAt, reason);
            try {
                bannedUserRepository.saveAndFlush(refreshed);
            } catch (ObjectOptimisticLockingFailureException retryEx) {
                throw new ConflictException("Ban state changed concurrently; please retry");
            }
        }
    }

    private void updateTimeoutWithRetry(TimedOutUser existing, Long streamId, Long userId, Long moderatorId,
                                        int durationSeconds, LocalDateTime expiresAt, String reason) {
        try {
            timedOutUserRepository.saveAndFlush(existing);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Optimistic-lock conflict: retry once against the freshest state.
            TimedOutUser refreshed = timedOutUserRepository
                    .findActiveTimeout(streamId, userId, LocalDateTime.now())
                    .orElseThrow(() -> new ConflictException(
                            "Timeout state changed concurrently; please retry"));
            applyTimeoutFields(refreshed, moderatorId, durationSeconds, expiresAt, reason);
            try {
                timedOutUserRepository.saveAndFlush(refreshed);
            } catch (ObjectOptimisticLockingFailureException retryEx) {
                throw new ConflictException("Timeout state changed concurrently; please retry");
            }
        }
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

    /**
     * Terminate a user's active sessions after a ban/timeout:
     * revoke every refresh token so they can no longer obtain a new
     * access token, and notify the user's connected WebSocket session.
     */
    public void forceDisconnect(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
        userRepository.findById(userId).ifPresent(user ->
                messagingTemplate.convertAndSendToUser(
                        user.getUsername(),
                        "/queue/events",
                        Map.of(
                                "action", "KICK",
                                "message", "Your session has been terminated by a moderator"
                        )
                ));
        log.info("Force disconnected user: {}", userId);
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
