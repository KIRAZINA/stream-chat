package com.streamchat.config;

import com.streamchat.repository.StreamRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.StreamAuthorizationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket configuration for real-time chat communication.
 * Uses STOMP protocol over SockJS for broad browser compatibility.
 *
 * 2.1 hardening (additive): explicit property-driven allowed origins (never
 * "*"), transport incoming-size / send-time / send-buffer / time-to-first-
 * message limits, and broker heartbeats driven by a dedicated TaskScheduler.
 * The JWT auth interceptor (CONNECT/SEND/SUBSCRIBE) and the /topic/stream/*
 * subscription enforcement are unchanged.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final StreamAuthorizationService streamAuthorizationService;
    private final StreamRepository streamRepository;

    @Value("${app.websocket.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.websocket.incoming-message-size-limit}")
    private int incomingMessageSizeLimit;

    @Value("${app.websocket.send-time-limit-ms}")
    private int sendTimeLimitMs;

    @Value("${app.websocket.send-buffer-size-limit}")
    private int sendBufferSizeLimit;

    @Value("${app.websocket.time-to-first-message-ms}")
    private int timeToFirstMessageMs;

    @Value("${app.websocket.heartbeat-interval-ms}")
    private long heartbeatIntervalMs;

    @Value("${app.websocket.scheduler-pool-size}")
    private int schedulerPoolSize;

    /**
     * Fail fast when the allowed-origins property is missing, empty, or
     * wildcarded. WebSocket origins must always be an explicit list; a
     * misconfigured deployment must refuse to start rather than widen.
     */
    @PostConstruct
    void validateAllowedOrigins() {
        parseOrigins();
    }

    /**
     * Parse the comma-separated origin list, rejecting any "*". Empty or
     * blank input yields an empty list so the caller can fail fast.
     */
    List<String> parseOrigins() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw new IllegalStateException(
                    "app.websocket.allowed-origins must be set to an explicit, comma-separated list of origins "
                            + "(never '*'); got: '" + allowedOrigins + "'");
        }
        List<String> origins = new ArrayList<>();
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("*".equals(trimmed)) {
                throw new IllegalStateException(
                        "app.websocket.allowed-origins must be an explicit list of origins, '*' is not allowed");
            }
            origins.add(trimmed);
        }
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "app.websocket.allowed-origins must contain at least one origin; got: '" + allowedOrigins + "'");
        }
        return origins;
    }

    /**
     * Configure message broker for pub/sub messaging.
     * /topic - for broadcasting to multiple subscribers
     * /queue - for point-to-point messaging
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for destinations prefixed with /topic and /queue
        config.enableSimpleBroker("/topic", "/queue")
                // Heartbeats require a TaskScheduler; without one no heartbeat
                // frames are emitted and dead connections are never reaped.
                .setHeartbeatValue(new long[]{heartbeatIntervalMs, heartbeatIntervalMs})
                .setTaskScheduler(heartbeatScheduler());

        // Set application destination prefix for @MessageMapping
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix for sending to specific users
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register STOMP endpoints for WebSocket connections.
     * Enables SockJS fallback options for browsers that don't support WebSocket.
     *
     * Track A · Item 1: a native WebSocket endpoint /ws-chat/stream/{streamKey}
     * is registered WITHOUT SockJS so the URL path carries the stream key and a
     * load balancer can consistent-hash on it. Spring 6.1 resolves the
     * {streamKey} URI template variable (WebSocketHandlerMapping extends
     * SimpleUrlHandlerMapping) and exposes it via HandlerMapping
     * .URI_TEMPLATE_VARIABLES_ATTRIBUTE, which StreamAffinityHandshakeInterceptor
     * reads to validate the stream and store the key in session attributes.
     * The legacy /ws-chat SockJS endpoint is preserved unchanged for rollback.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(parseOrigins().toArray(new String[0]))
                .withSockJS();

        registry.addEndpoint("/ws-chat/stream/{streamKey}")
                .setAllowedOriginPatterns(parseOrigins().toArray(new String[0]))
                .addInterceptors(streamAffinityHandshakeInterceptor());
    }

    /**
     * Handshake interceptor shared by the stream-keyed endpoint: validates the
     * {streamKey} path variable refers to an existing stream and stashes the
     * key in session attributes. Runs on every connection to /ws-chat/stream/*
     * so it deliberately performs a cheap exists-by-key lookup.
     */
    @Bean
    public StreamAffinityHandshakeInterceptor streamAffinityHandshakeInterceptor() {
        return new StreamAffinityHandshakeInterceptor(streamRepository);
    }

    /**
     * Incoming/outgoing transport limits: cap the largest accepted frame,
     * bound how long a send may block flushing to a slow session, bound how
     * much may be buffered per session, and require the first client frame
     * within a time window. Does not touch the JWT interceptor.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(incomingMessageSizeLimit)
                .setSendTimeLimit(sendTimeLimitMs)
                .setSendBufferSizeLimit(sendBufferSizeLimit)
                .setTimeToFirstMessage(timeToFirstMessageMs);
    }

    /**
     * Configure inbound channel to add JWT authentication interceptor.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                // Authenticate on CONNECT and preserve for all subsequent messages
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                    if (accessor.getUser() == null) {
                        throw new SecurityException("WebSocket authentication required");
                    }
                }

                if (StompCommand.SEND.equals(accessor.getCommand()) ||
                    StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null) {
                        authenticate(accessor);
                    }
                    if (accessor.getUser() == null) {
                        throw new SecurityException("WebSocket authentication required");
                    }
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    enforceSubscriptionAccess(accessor);
                }

                return message;
            }

            private void enforceSubscriptionAccess(StompHeaderAccessor accessor) {
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith("/topic/stream/")) {
                    return;
                }
                String streamKey = destination.substring("/topic/stream/".length());
                int slash = streamKey.indexOf('/');
                if (slash >= 0) {
                    streamKey = streamKey.substring(0, slash);
                }
                String username = accessor.getUser() != null ? accessor.getUser().getName() : null;
                streamAuthorizationService.assertCanAccessHistory(streamKey, username);
            }

            private void authenticate(StompHeaderAccessor accessor) {
                // Get JWT token from Authorization header
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                    return;
                }
                String token = authHeader.substring(7);
                if (!tokenProvider.validateToken(token)) {
                    return;
                }
                String username = tokenProvider.getUsernameFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                accessor.setUser(authentication);
            }
        });
    }

    /**
     * TaskScheduler that drives broker heartbeat emission and dead-session
     * reaping. Pool size is deliberately small (heartbeat ticks only).
     */
    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
