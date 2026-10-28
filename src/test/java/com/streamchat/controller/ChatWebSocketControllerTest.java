package com.streamchat.controller;

import com.streamchat.exception.ErrorResponse;
import com.streamchat.exception.RateLimitException;
import com.streamchat.exception.ResourceNotFoundException;
import com.streamchat.exception.UnauthorizedException;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.service.ChatService;
import com.streamchat.service.MessageBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;
    @Mock
    private MessageBroadcastService messageBroadcastService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private Principal principal;

    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatWebSocketController(chatService, messageBroadcastService, messagingTemplate);
    }

    @Test
    void sendMessage_success_persistsAndBroadcasts() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder()
                .content("hello")
                .idempotencyKey("k1")
                .replyToMessageId(42L)
                .build();
        ChatMessageDTO saved = ChatMessageDTO.builder().id(1L).content("hello").build();
        when(chatService.sendMessage("stream-1", "alice", "hello", MessageType.CHAT, 42L, "k1"))
                .thenReturn(saved);

        controller.sendMessage("stream-1", dto, principal);

        verify(messageBroadcastService).broadcastMessage("stream-1", saved, false);
    }

    @Test
    void sendMessage_invalidContent_sends400ErrorResponseWithPathAndIdempotencyKey() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder().content("hello").idempotencyKey("k9").build();
        when(chatService.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad content"));

        controller.sendMessage("stream-1", dto, principal);

        ErrorResponse error = captureUserError("alice");
        assertEquals(400, error.getStatus());
        assertEquals("Bad Request", error.getError());
        assertEquals("bad content", error.getMessage());
        assertEquals("/app/chat.send/stream-1", error.getPath());
        assertEquals("k9", error.getIdempotencyKey(),
                "error frame must echo the idempotencyKey so the client can discard its optimistic message");
        assertNotNull(error.getTimestamp());
    }

    @Test
    void sendMessage_rateLimited_sends429ErrorResponse() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder().content("spam").idempotencyKey("rate-1").build();
        when(chatService.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RateLimitException("Slow mode is enabled. Wait 5 more seconds."));

        controller.sendMessage("stream-1", dto, principal);

        ErrorResponse error = captureUserError("alice");
        assertEquals(429, error.getStatus());
        assertEquals("Rate Limit Exceeded", error.getError());
        assertTrue(error.getMessage().contains("Slow mode"));
        assertEquals("/app/chat.send/stream-1", error.getPath());
        assertEquals("rate-1", error.getIdempotencyKey());
    }

    @Test
    void sendMessage_unauthenticated_sends401ErrorResponse() {
        ChatMessageDTO dto = ChatMessageDTO.builder().content("hello").idempotencyKey("anon-1").build();

        controller.sendMessage("stream-1", dto, null);

        ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/user/queue/errors"), captor.capture());

        ErrorResponse error = captor.getValue();
        assertEquals(401, error.getStatus());
        assertEquals("Unauthorized", error.getError());
        assertEquals("Authentication required", error.getMessage());
        assertEquals("/app/chat.send/stream-1", error.getPath());
        assertEquals("anon-1", error.getIdempotencyKey());
        verifyNoInteractions(chatService, messageBroadcastService);
    }

    @Test
    void sendMessage_resourceNotFound_sends404ErrorResponse() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder().content("hello").idempotencyKey("nf-1").build();
        when(chatService.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Stream not found"));

        controller.sendMessage("missing-stream", dto, principal);

        ErrorResponse error = captureUserError("alice");
        assertEquals(404, error.getStatus());
        assertEquals("Not Found", error.getError());
        assertEquals("Stream not found", error.getMessage());
        assertEquals("/app/chat.send/missing-stream", error.getPath());
        assertEquals("nf-1", error.getIdempotencyKey());
    }

    @Test
    void sendMessage_unauthorized_sends401ErrorResponse() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder().content("hello").idempotencyKey("u-1").build();
        when(chatService.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new UnauthorizedException("User is banned from this chat"));

        controller.sendMessage("stream-1", dto, principal);

        ErrorResponse error = captureUserError("alice");
        assertEquals(401, error.getStatus());
        assertEquals("Unauthorized", error.getError());
        assertEquals("User is banned from this chat", error.getMessage());
        assertEquals("u-1", error.getIdempotencyKey());
    }

    @Test
    void sendMessage_unexpectedFailure_sends500ErrorResponseWithoutLeakingDetails() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder().content("hello").idempotencyKey("e-1").build();
        when(chatService.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("database exploded"));

        controller.sendMessage("stream-1", dto, principal);

        ErrorResponse error = captureUserError("alice");
        assertEquals(500, error.getStatus());
        assertEquals("Internal Server Error", error.getError());
        assertEquals("An unexpected error occurred", error.getMessage());
        assertEquals("e-1", error.getIdempotencyKey());
    }

    private ErrorResponse captureUserError(String username) {
        ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(username), eq("/queue/errors"), captor.capture());
        return captor.getValue();
    }
}
