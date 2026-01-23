package com.streamchat.config;

import com.streamchat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket configuration for real-time chat communication.
 * Uses STOMP protocol over SockJS for broad browser compatibility.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Configure message broker for pub/sub messaging.
     * /topic - for broadcasting to multiple subscribers
     * /queue - for point-to-point messaging
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for destinations prefixed with /topic and /queue
        config.enableSimpleBroker("/topic", "/queue");

        // Set application destination prefix for @MessageMapping
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix for sending to specific users
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register STOMP endpoints for WebSocket connections.
     * Enables SockJS fallback options for browsers that don't support WebSocket.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
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

                // Authenticate on CONNECT and preserve for all subsequent messages
                if (StompCommand.CONNECT.equals(accessor.getCommand()) || 
                    StompCommand.SEND.equals(accessor.getCommand()) ||
                    StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    
                    // Get JWT token from Authorization header
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);

                        if (tokenProvider.validateToken(token)) {
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
                    }
                }

                return message;
            }
        });
    }
}