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

/**
 * Tracks real-time WebSocket subscriptions for stream presence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketPresenceEventListener {

    private static final String STREAM_TOPIC_PREFIX = "/topic/stream/";
    private final PresenceService presenceService;

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
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presenceService.removeSubscription(sessionId);
    }
}
