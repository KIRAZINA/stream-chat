package com.streamchat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks active WebSocket viewers per stream.
 * Supports lightweight presence count and join/leave reduction.
 */
@Service
@Slf4j
public class PresenceService {

    private final ConcurrentMap<String, String> sessionToStreamKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> sessionToUsername = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Integer>> streamUserSessionCounts = new ConcurrentHashMap<>();

    /**
     * Register a stream subscription for a session.
     * Returns true when this user becomes active for the stream.
     */
    public boolean registerSubscription(String sessionId, String username, String streamKey) {
        if (sessionId == null || username == null || streamKey == null) {
            return false;
        }

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
        log.debug("Registered subscription: stream={}, user={}, session={}, count={}", streamKey, username, sessionId, currentCount);
        return currentCount == 1;
    }

    /**
     * Remove the session subscription and decrement active viewer counts.
     * Returns true when the user is no longer active in the stream.
     */
    public boolean removeSubscription(String sessionId) {
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
        log.debug("Removed subscription: stream={}, user={}, session={}, userLeft={}", streamKey, username, sessionId, userLeft);
        return userLeft;
    }

    /**
     * Get the number of unique active viewers in a stream.
     */
    public int getActiveViewers(String streamKey) {
        Map<String, Integer> counts = streamUserSessionCounts.get(streamKey);
        return counts == null ? 0 : counts.size();
    }
}
