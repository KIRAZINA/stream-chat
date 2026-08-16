package com.streamchat.listener;

import com.streamchat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks real-time WebSocket subscriptions for stream presence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketPresenceEventListener {

    private static final String STREAM_TOPIC_PREFIX = "/topic/stream/";
    private final PresenceService presenceService;

    // Lightweight in-memory per-stream subscriber counts used by
    // MessageBroadcastService to detect orphaned local broadcasts.
    private final ConcurrentMap<String, AtomicInteger> streamSubscriberCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> chatTopicSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        Message<byte[]> message = (Message<byte[]>) event.getMessage();
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getUser() == null) {
            return;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(STREAM_TOPIC_PREFIX)) {
            return;
        }

        String sessionId = accessor.getSessionId();
        String username = accessor.getUser().getName();
        String streamKey = destination.substring(STREAM_TOPIC_PREFIX.length());
        presenceService.registerSubscription(sessionId, username, streamKey);

        if (isChatTopic(destination, streamKey)) {
            chatTopicSessions.put(sessionId, streamKey);
            streamSubscriberCounts.computeIfAbsent(streamKey, key -> new AtomicInteger()).incrementAndGet();
        }
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        Message<byte[]> message = (Message<byte[]>) event.getMessage();
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }

        String sessionId = accessor.getSessionId();
        presenceService.removeSubscription(sessionId);
        decrementSubscriber(sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presenceService.removeSubscription(sessionId);
        decrementSubscriber(sessionId);
    }

    /**
     * Number of sessions currently subscribed to the chat topic of a stream.
     */
    public int getChatSubscriberCount(String streamKey) {
        AtomicInteger count = streamSubscriberCounts.get(streamKey);
        return count == null ? 0 : count.get();
    }

    private boolean isChatTopic(String destination, String streamKey) {
        return destination.equals(STREAM_TOPIC_PREFIX + streamKey);
    }

    private void decrementSubscriber(String sessionId) {
        String streamKey = chatTopicSessions.remove(sessionId);
        if (streamKey == null) {
            return;
        }
        AtomicInteger count = streamSubscriberCounts.get(streamKey);
        if (count != null && count.decrementAndGet() <= 0) {
            streamSubscriberCounts.remove(streamKey);
        }
    }
}
