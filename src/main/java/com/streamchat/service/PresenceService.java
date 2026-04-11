package com.streamchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks active WebSocket viewers per stream.
 * Supports both in-memory (single-instance) and Redis-backed (distributed) presence.
 */
@Service
@Slf4j
public class PresenceService {

    // In-memory presence (single-instance fallback)
    private final ConcurrentMap<String, String> sessionToStreamKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> sessionToUsername = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Integer>> streamUserSessionCounts = new ConcurrentHashMap<>();

    // Redis-backed presence (distributed)
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final String PRESENCE_KEY = "presence:stream:";
    private static final String SESSION_KEY = "presence:session:";
    private static final long PRESENCE_TTL_SECONDS = 300; // 5 minutes

    /**
     * Register a stream subscription for a session.
     * Returns true when this user becomes active for the stream.
     */
    public boolean registerSubscription(String sessionId, String username, String streamKey) {
        if (sessionId == null || username == null || streamKey == null) {
            return false;
        }

        if (redisTemplate != null) {
            return registerSubscriptionRedis(sessionId, username, streamKey);
        }

        return registerSubscriptionMemory(sessionId, username, streamKey);
    }

    private boolean registerSubscriptionMemory(String sessionId, String username, String streamKey) {
        String existingStream = sessionToStreamKey.putIfAbsent(sessionId, streamKey);
        if (existingStream != null) {
            return false;
        }

        sessionToUsername.put(sessionId, username);
        ConcurrentMap<String, Integer> counts = streamUserSessionCounts.computeIfAbsent(
                streamKey,
                key -> new ConcurrentHashMap<>()
        );

        int currentCount = counts.merge(username, 1, Integer::sum);
        log.debug("Registered subscription (memory): stream={}, user={}, session={}, count={}",
                streamKey, username, sessionId, currentCount);
        return currentCount == 1;
    }

    private boolean registerSubscriptionRedis(String sessionId, String username, String streamKey) {
        try {
            String sessionStreamKey = SESSION_KEY + sessionId;
            String existingStream = (String) redisTemplate.opsForValue().get(sessionStreamKey);
            if (existingStream != null) {
                return false;
            }

            // Store session -> streamKey mapping
            redisTemplate.opsForValue().set(sessionStreamKey, streamKey, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(SESSION_KEY + sessionId + ":user", username, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);

            // Increment presence counter
            String presenceKey = PRESENCE_KEY + streamKey + ":" + username;
            Long count = redisTemplate.opsForValue().increment(presenceKey);
            redisTemplate.expire(presenceKey, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);

            boolean firstSession = count != null && count == 1;
            log.debug("Registered subscription (Redis): stream={}, user={}, session={}, first={}",
                    streamKey, username, sessionId, firstSession);
            return firstSession;
        } catch (Exception e) {
            log.warn("Failed to register subscription in Redis, falling back to memory: {}", e.getMessage());
            return registerSubscriptionMemory(sessionId, username, streamKey);
        }
    }

    /**
     * Remove the session subscription and decrement active viewer counts.
     * Returns true when the user is no longer active in the stream.
     */
    public boolean removeSubscription(String sessionId) {
        if (redisTemplate != null) {
            return removeSubscriptionRedis(sessionId);
        }

        return removeSubscriptionMemory(sessionId);
    }

    private boolean removeSubscriptionMemory(String sessionId) {
        String streamKey = sessionToStreamKey.remove(sessionId);
        String username = sessionToUsername.remove(sessionId);

        if (streamKey == null || username == null) {
            return false;
        }

        ConcurrentMap<String, Integer> counts = streamUserSessionCounts.get(streamKey);
        if (counts == null) {
            return false;
        }

        counts.computeIfPresent(username, (key, value) -> value <= 1 ? null : value - 1);
        if (counts.isEmpty()) {
            streamUserSessionCounts.remove(streamKey);
        }

        boolean userLeft = !counts.containsKey(username);
        log.debug("Removed subscription (memory): stream={}, user={}, session={}, userLeft={}",
                streamKey, username, sessionId, userLeft);
        return userLeft;
    }

    private boolean removeSubscriptionRedis(String sessionId) {
        try {
            String sessionStreamKey = SESSION_KEY + sessionId;
            String streamKey = (String) redisTemplate.opsForValue().get(sessionStreamKey);
            String username = (String) redisTemplate.opsForValue().get(sessionStreamKey + ":user");

            if (streamKey == null || username == null) {
                return removeSubscriptionMemory(sessionId);
            }

            // Remove session keys
            redisTemplate.delete(sessionStreamKey);
            redisTemplate.delete(sessionStreamKey + ":user");

            // Decrement presence counter
            String presenceKey = PRESENCE_KEY + streamKey + ":" + username;
            Long count = redisTemplate.opsForValue().decrement(presenceKey);
            if (count == null || count <= 0) {
                redisTemplate.delete(presenceKey);
            }

            log.debug("Removed subscription (Redis): stream={}, user={}, session={}",
                    streamKey, username, sessionId);
            return count == null || count <= 0;
        } catch (Exception e) {
            log.warn("Failed to remove subscription from Redis, falling back to memory: {}", e.getMessage());
            return removeSubscriptionMemory(sessionId);
        }
    }

    /**
     * Get the number of unique active viewers in a stream.
     */
    public int getActiveViewers(String streamKey) {
        if (redisTemplate != null) {
            return getActiveViewersRedis(streamKey);
        }

        Map<String, Integer> counts = streamUserSessionCounts.get(streamKey);
        return counts == null ? 0 : counts.size();
    }

    private int getActiveViewersRedis(String streamKey) {
        try {
            // Count unique users by scanning keys with pattern
            String pattern = PRESENCE_KEY + streamKey + ":*";
            int size = redisTemplate.keys(pattern).size();
            return size;
        } catch (Exception e) {
            log.warn("Failed to get active viewers from Redis, falling back to memory: {}", e.getMessage());
            Map<String, Integer> counts = streamUserSessionCounts.get(streamKey);
            return counts == null ? 0 : counts.size();
        }
    }
}
