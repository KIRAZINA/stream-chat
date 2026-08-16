package com.streamchat.service;

import com.streamchat.listener.WebSocketPresenceEventListener;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AMENDMENT 2 pin: the DEFAULT (property absent) of
 * {@code chat.broadcast.local-first} must be {@code false}.
 *
 * <p>This context loads {@link MessageBroadcastService} WITHOUT setting
 * {@code chat.broadcast.local-first} anywhere, so the resolved value comes from
 * the {@code @Value("${chat.broadcast.local-first:false}")} fallback, not from
 * {@code application.properties}. If the default is ever silently flipped to
 * {@code true} before the consistent-hash LB (A4) is live, multi-instance chat
 * breaks and nothing else in the suite catches it — this test is that guard.
 */
@SpringJUnitConfig(MessageBroadcastServiceDefaultValueTest.Config.class)
class MessageBroadcastServiceDefaultValueTest {

    @Configuration
    static class Config {

        @Bean
        MessageBroadcastService service(SimpMessagingTemplate template,
                                        ObjectProvider<RedisMessagePublisher> publisherProvider,
                                        WebSocketPresenceEventListener presence,
                                        MetricsService metrics) {
            return new MessageBroadcastService(template, publisherProvider, presence, metrics);
        }

        @Bean
        SimpMessagingTemplate messagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        RedisMessagePublisher redisPublisher() {
            return mock(RedisMessagePublisher.class);
        }

        @Bean
        WebSocketPresenceEventListener presenceEventListener() {
            return mock(WebSocketPresenceEventListener.class);
        }

        @Bean
        MetricsService metricsService() {
            return mock(MetricsService.class);
        }
    }

    @Value("${chat.broadcast.local-first:false}")
    private boolean localFirst;

    @org.springframework.beans.factory.annotation.Autowired
    private MessageBroadcastService service;

    @org.springframework.beans.factory.annotation.Autowired
    private SimpMessagingTemplate messagingTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private RedisMessagePublisher redisPublisher;

    @Test
    void defaultProperty_isAbsent_resolvesToFalse() {
        assertFalse(localFirst, "chat.broadcast.local-first default must be false");
    }

    @Test
    void defaultBehavior_routesViaRedis_withoutDirectSend() {
        when(redisPublisher.publish(anyString(), any())).thenReturn(true);

        service.broadcastMessage("stream-1",
                ChatMessageDTO.builder()
                        .id(1L)
                        .username("testuser")
                        .content("Hello")
                        .messageType(MessageType.CHAT)
                        .build(),
                false);

        verify(redisPublisher).publish(eq("stream-1"), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}