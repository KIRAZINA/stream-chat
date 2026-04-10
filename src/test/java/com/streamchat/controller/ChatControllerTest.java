package com.streamchat.controller;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import com.streamchat.service.RedisMessagePublisher;
import com.streamchat.service.StreamAuthorizationService;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ModerationService moderationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StreamAuthorizationService streamAuthorizationService;

    @Mock
    private ObjectProvider<RedisMessagePublisher> redisMessagePublisherProvider;

    @Mock
    private RedisMessagePublisher redisMessagePublisher;

    @InjectMocks
    private ChatController chatController;

    private Principal principal;
    private ChatMessageDTO savedMessage;

    @BeforeEach
    void setUp() {
        principal = () -> "testuser";
        savedMessage = ChatMessageDTO.builder()
                .id(1L)
                .username("testuser")
                .content("Hello, world!")
                .messageType(MessageType.CHAT)
                .build();
    }

    @Test
    void sendMessage_WithoutRedisPublisher_BroadcastsLocally() {
        when(chatService.sendMessage("stream-1", "testuser", "Hello, world!", MessageType.CHAT))
                .thenReturn(savedMessage);
        when(redisMessagePublisherProvider.getIfAvailable()).thenReturn(null);

        chatController.sendMessage("stream-1", ChatMessageDTO.builder().content("Hello, world!").build(), principal);

        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", savedMessage);
        verify(redisMessagePublisherProvider).getIfAvailable();
        verifyNoInteractions(redisMessagePublisher);
    }

    @Test
    void sendMessage_WithRedisPublisher_PublishesWithoutLocalBroadcast() {
        when(chatService.sendMessage("stream-1", "testuser", "Hello, world!", MessageType.CHAT))
                .thenReturn(savedMessage);
        when(redisMessagePublisherProvider.getIfAvailable()).thenReturn(redisMessagePublisher);
        when(redisMessagePublisher.publish("stream-1", savedMessage)).thenReturn(true);

        chatController.sendMessage("stream-1", ChatMessageDTO.builder().content("Hello, world!").build(), principal);

        verify(redisMessagePublisher).publish("stream-1", savedMessage);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void sendMessage_WhenRedisPublishFails_FallsBackToLocalBroadcast() {
        when(chatService.sendMessage("stream-1", "testuser", "Hello, world!", MessageType.CHAT))
                .thenReturn(savedMessage);
        when(redisMessagePublisherProvider.getIfAvailable()).thenReturn(redisMessagePublisher);
        when(redisMessagePublisher.publish("stream-1", savedMessage)).thenReturn(false);

        chatController.sendMessage("stream-1", ChatMessageDTO.builder().content("Hello, world!").build(), principal);

        verify(redisMessagePublisher).publish("stream-1", savedMessage);
        verify(messagingTemplate).convertAndSend("/topic/stream/stream-1", savedMessage);
    }

    @Test
    void sendMessage_Unauthenticated_DoesNothing() {
        chatController.sendMessage("stream-1", ChatMessageDTO.builder().content("Hello").build(), null);

        verifyNoInteractions(chatService, messagingTemplate, redisMessagePublisherProvider, redisMessagePublisher);
    }
}
