package com.streamchat.controller;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.service.ChatService;
import com.streamchat.service.MessageBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private MessageBroadcastService messageBroadcastService;

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
    void sendMessage_ValidRequest_ReturnsMessage() {
        ChatController.SendMessageRequest request = new ChatController.SendMessageRequest();
        request.setContent("Hello, world!");
        request.setMessageType(MessageType.CHAT);

        when(chatService.sendMessage("stream-1", "testuser", "Hello, world!", MessageType.CHAT, null, null))
                .thenReturn(savedMessage);

        ChatMessageDTO result = chatController.sendMessage("stream-1", request, principal).getBody();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Hello, world!", result.getContent());
        // REST sends must force the Redis path so every instance broadcasts.
        verify(messageBroadcastService).broadcastMessage(eq("stream-1"), eq(savedMessage), eq(true));
    }

    @Test
    void sendMessage_NullMessageType_DefaultsToChat() {
        ChatController.SendMessageRequest request = new ChatController.SendMessageRequest();
        request.setContent("Hello, world!");

        when(chatService.sendMessage("stream-1", "testuser", "Hello, world!", MessageType.CHAT, null, null))
                .thenReturn(savedMessage);

        ChatMessageDTO result = chatController.sendMessage("stream-1", request, principal).getBody();

        assertNotNull(result);
        ArgumentCaptor<MessageType> typeCaptor = ArgumentCaptor.forClass(MessageType.class);
        verify(chatService).sendMessage(eq("stream-1"), eq("testuser"), eq("Hello, world!"),
                typeCaptor.capture(), isNull(), isNull());
        assertEquals(MessageType.CHAT, typeCaptor.getValue());
    }

    @Test
    void sendMessage_WithReplyAndIdempotencyKey_DelegatesCorrectly() {
        ChatController.SendMessageRequest request = new ChatController.SendMessageRequest();
        request.setContent("Reply message");
        request.setMessageType(MessageType.CHAT);
        request.setReplyToMessageId(42L);
        request.setIdempotencyKey("req-abc-123");

        when(chatService.sendMessage("stream-1", "testuser", "Reply message", MessageType.CHAT, 42L, "req-abc-123"))
                .thenReturn(savedMessage);

        ChatMessageDTO result = chatController.sendMessage("stream-1", request, principal).getBody();

        assertNotNull(result);
        verify(chatService).sendMessage("stream-1", "testuser", "Reply message",
                MessageType.CHAT, 42L, "req-abc-123");
    }
}
