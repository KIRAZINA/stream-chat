package com.streamchat.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for implementing rate limiting to prevent spam.
 * Uses Redis when available, falls back to in-memory storage.
 */
@Service
@Slf4j
public class RateLimitService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final int DEFAULT_WINDOW_SECONDS = 60;
    private static final String RATE_LIMIT_KEY = "ratelimit:";
    private static final int CLEANUP_THRESHOLD = 10000;

    // In-memory fallback when Redis is not available
    private final Map<String, RateLimitEntry> inMemoryRateLimits = new ConcurrentHashMap<>();

    /**
     * Check if a user is allowed to send a message based on rate limits.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return true if user can send message
     */
    public boolean allowMessage(Long streamId, Long userId) {
        return allowMessage(streamId, userId, DEFAULT_MAX_MESSAGES, DEFAULT_WINDOW_SECONDS);
    }

    /**
     * Check rate limit with custom parameters.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @param maxMessages maximum messages allowed in window
     * @param windowSeconds time window in seconds
     * @return true if user can send message
     */
    public boolean allowMessage(Long streamId, Long userId,
                                int maxMessages, int windowSeconds) {
        String key = RATE_LIMIT_KEY + streamId + ":" + userId;

        // Use Redis if available
        if (redisTemplate != null) {
            try {
                return checkRedisRateLimit(key, maxMessages, windowSeconds);
            } catch (Exception e) {
                log.warn("Failed to check rate limit in Redis, falling back to in-memory: {}",
                        e.getMessage());
            }
        }

        // Fallback to in-memory
        return checkInMemoryRateLimit(key, maxMessages, windowSeconds);
    }

    /**
     * Check rate limit using Redis.
     */
    private boolean checkRedisRateLimit(String key, int maxMessages, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            log.error("Failed to increment rate limit counter for key: {}", key);
            return true; // Fail open
        }

        // Set expiration on first message
        if (count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        boolean allowed = count <= maxMessages;

        if (!allowed) {
            log.warn("Rate limit exceeded (Redis): key={}, count={}/{}",
                    key, count, maxMessages);
        }

        return allowed;
    }

    /**
     * Check rate limit using in-memory storage.
     */
    private boolean checkInMemoryRateLimit(String key, int maxMessages, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        if (inMemoryRateLimits.size() > CLEANUP_THRESHOLD) {
            cleanupExpiredEntries(windowSeconds);
        }

        RateLimitEntry entry = inMemoryRateLimits.compute(key, (k, v) -> {
            if (v == null || v.getWindowStart() < windowStart) {
                // New window
                return new RateLimitEntry(now, 1);
            } else {
                // Increment existing window
                v.increment();
                return v;
            }
        });

        boolean allowed = entry.getCount() <= maxMessages;

        if (!allowed) {
            log.warn("Rate limit exceeded (in-memory): key={}, count={}/{}",
                    key, entry.getCount(), maxMessages);
        }

        return allowed;
    }

    private void cleanupExpiredEntries(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        inMemoryRateLimits.entrySet().removeIf(e -> e.getValue().getWindowStart() < cutoff);
    }

    /**
     * Reset rate limit for a user (used when granting immunity like subscriber status).
     *
     * @param streamId the stream ID
     * @param userId the user ID
     */
    public void resetRateLimit(Long streamId, Long userId) {
        String key = RATE_LIMIT_KEY + streamId + ":" + userId;

        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Failed to reset rate limit in Redis: {}", e.getMessage());
            }
        }

        inMemoryRateLimits.remove(key);
        log.debug("Rate limit reset: streamId={}, userId={}", streamId, userId);
    }

    /**
     * Get remaining messages allowed for a user.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return number of messages remaining
     */
    public int getRemainingMessages(Long streamId, Long userId) {
        String key = RATE_LIMIT_KEY + streamId + ":" + userId;

        if (redisTemplate != null) {
            try {
                Object value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    return DEFAULT_MAX_MESSAGES;
                }
                int used = Integer.parseInt(value.toString());
                return Math.max(0, DEFAULT_MAX_MESSAGES - used);
            } catch (Exception e) {
                log.warn("Failed to get remaining messages from Redis: {}", e.getMessage());
            }
        }

        // Fallback to in-memory
        RateLimitEntry entry = inMemoryRateLimits.get(key);
        if (entry == null) {
            return DEFAULT_MAX_MESSAGES;
        }

        // Check if window expired
        long now = System.currentTimeMillis();
        if (now - entry.getWindowStart() > DEFAULT_WINDOW_SECONDS * 1000L) {
            return DEFAULT_MAX_MESSAGES;
        }

        return Math.max(0, DEFAULT_MAX_MESSAGES - entry.getCount());
    }

    /**
     * Inner class for tracking rate limits in memory.
     */
    @Getter
    @AllArgsConstructor
    private static class RateLimitEntry {
        private long windowStart;
        private int count;

        public void increment() {
            count++;
        }
    }
}
