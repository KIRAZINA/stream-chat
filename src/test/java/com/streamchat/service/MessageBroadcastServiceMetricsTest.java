package com.streamchat.service;

import com.streamchat.listener.WebSocketPresenceEventListener;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Track A · Item 5 (A5.1): the local-first broadcast counters are
 * Micrometer-registered on a REAL registry (not a mocked MetricsService) and
 * increment under real local-first delivery.
 *
 * <p>The Prometheus rendering of these meters is asserted separately in
 * {@code PrometheusMetricsSmokeIntegrationTest} (A5.4); the mapping is
 * deterministic: Micrometer {@code chat.broadcast.local} →
 * Prometheus {@code chat_broadcast_local_total}.
 */
@SpringJUnitConfig(MessageBroadcastServiceMetricsTest.Config.class)
@TestPropertySource(properties = "chat.broadcast.local-first=true")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MessageBroadcastServiceMetricsTest {

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
        MetricsService metricsService(SimpleMeterRegistry registry) {
            return new MetricsService(registry);
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
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
    }

    @Autowired
    private MessageBroadcastService service;

    @Autowired
    private SimpleMeterRegistry registry;

    @Autowired
    private WebSocketPresenceEventListener presence;

    private ChatMessageDTO message() {
        return ChatMessageDTO.builder()
                .id(1L)
                .username("alice")
                .content("hello")
                .messageType(MessageType.CHAT)
                .build();
    }

    @Test
    void localFirstBroadcast_incrementsBroadcastLocalCounter() {
        when(presence.getChatSubscriberCount("stream-1")).thenReturn(2);

        service.broadcastMessage("stream-1", message(), false);

        Counter local = registry.find("chat.broadcast.local").counter();
        assertNotNull(local, "chat.broadcast.local must be Micrometer-registered");
        assertEquals(1.0, local.count(),
                "a local-first delivery with subscribers must increment chat.broadcast.local");
    }

    @Test
    void orphanedBroadcast_incrementsOrphanedCounterAndWarns(CapturedOutput output) {
        when(presence.getChatSubscriberCount("stream-1")).thenReturn(0);

        service.broadcastMessage("stream-1", message(), false);

        Counter local = registry.find("chat.broadcast.local").counter();
        assertNotNull(local, "chat.broadcast.local must be Micrometer-registered");
        assertEquals(1.0, local.count(),
                "a zero-subscriber local broadcast still counts as a local delivery");

        Counter orphaned = registry.find("chat.broadcast.orphaned").counter();
        assertNotNull(orphaned, "chat.broadcast.orphaned must be Micrometer-registered");
        assertEquals(1.0, orphaned.count(),
                "a zero-subscriber local broadcast must increment chat.broadcast.orphaned");

        assertTrue(output.getOut().contains("zero local subscribers"),
                "the orphaned broadcast must WARN, naming the zero-subscriber stream");
    }
}