package com.streamchat.service;

import com.streamchat.listener.WebSocketPresenceEventListener;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ObjectProvider<RedisMessagePublisher> publisherProvider;
    @Mock
    private RedisMessagePublisher redisMessagePublisher;
    @Mock
    private WebSocketPresenceEventListener presenceEventListener;
    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private MessageBroadcastService service;

    private ChatMessageDTO message;

    @BeforeEach
    void setUp() {
        message = ChatMessageDTO.builder()
                .id(1L)
                .username("testuser")
                .content("Hello")
                .messageType(MessageType.CHAT)
                .build();
    }

    @Test
    void localFirst_True_DeliversLocally_WithoutRedis() {
        ReflectionTestUtils.setField(service, "localFirst", true);

        service.broadcastMessage("stream-1", message, false);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", message);
        verify(redisMessagePublisher, never()).publish(anyString(), any());
        verify(publisherProvider, never()).getIfAvailable();
        verify(metricsService).recordBroadcastLocal();
    }

    @Test
    void localFirst_False_PublishesViaRedis() {
        ReflectionTestUtils.setField(service, "localFirst", false);
        when(publisherProvider.getIfAvailable()).thenReturn(redisMessagePublisher);
        when(redisMessagePublisher.publish("stream-1", message)).thenReturn(true);

        service.broadcastMessage("stream-1", message, false);

        verify(redisMessagePublisher).publish("stream-1", message);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/stream/stream-1"), any(Object.class));
    }

    @Test
    void forceRedis_True_IgnoresLocalFirst() {
        ReflectionTestUtils.setField(service, "localFirst", true);
        when(publisherProvider.getIfAvailable()).thenReturn(redisMessagePublisher);
        when(redisMessagePublisher.publish("stream-1", message)).thenReturn(true);

        service.broadcastMessage("stream-1", message, true);

        verify(redisMessagePublisher).publish("stream-1", message);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/stream/stream-1"), any(Object.class));
    }

    @Test
    void localFirst_True_NoSubscribers_RecordsOrphaned() {
        ReflectionTestUtils.setField(service, "localFirst", true);
        when(presenceEventListener.getChatSubscriberCount("stream-1")).thenReturn(0);

        service.broadcastMessage("stream-1", message, false);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", message);
        verify(metricsService).recordBroadcastOrphaned();
    }

    @Test
    void localFirst_True_WithSubscribers_DoesNotRecordOrphaned() {
        ReflectionTestUtils.setField(service, "localFirst", true);
        when(presenceEventListener.getChatSubscriberCount("stream-1")).thenReturn(3);

        service.broadcastMessage("stream-1", message, false);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", message);
        verify(metricsService, never()).recordBroadcastOrphaned();
    }

    @Test
    void noRedisPublisher_FallsBackToLocal() {
        ReflectionTestUtils.setField(service, "localFirst", false);
        when(publisherProvider.getIfAvailable()).thenReturn(null);

        service.broadcastMessage("stream-1", message, false);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", message);
    }

    @Test
    void publishFailure_FallsBackToLocal() {
        ReflectionTestUtils.setField(service, "localFirst", false);
        when(publisherProvider.getIfAvailable()).thenReturn(redisMessagePublisher);
        when(redisMessagePublisher.publish("stream-1", message)).thenReturn(false);

        service.broadcastMessage("stream-1", message, false);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", message);
    }

    @Test
    void broadcastEvent_AlwaysLocal() {
        service.broadcastEvent("stream-1", message);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1/events", message);
        verify(redisMessagePublisher, never()).publish(anyString(), any());
    }
}
