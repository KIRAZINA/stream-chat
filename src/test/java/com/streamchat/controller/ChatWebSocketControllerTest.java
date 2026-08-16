package com.streamchat.controller;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    void sendMessage_failure_notifiesSenderOnErrorsQueue() {
        when(principal.getName()).thenReturn("alice");
        ChatMessageDTO dto = ChatMessageDTO.builder().content("hello").build();
        when(chatService.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad content"));

        controller.sendMessage("stream-1", dto, principal);

        ArgumentCaptor<ChatMessageDTO> captor = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), captor.capture());
        assertEquals(MessageType.ERROR, captor.getValue().getMessageType());
        assertEquals("bad content", captor.getValue().getContent());
    }
}