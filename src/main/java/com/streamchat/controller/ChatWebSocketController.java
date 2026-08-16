package com.streamchat.controller;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.service.ChatService;
import com.streamchat.service.MessageBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket (STOMP) controller for real-time chat operations.
 *
 * <p>Handles the STOMP destinations the frontend already publishes to:
 * {@code /app/chat.send/{streamKey}}, {@code /app/chat.join/{streamKey}} and
 * {@code /app/chat.leave/{streamKey}} (application prefix {@code /app}, see
 * {@code WebSocketConfig#configureMessageBroker}).
 *
 * <p>The identity is always taken from the authenticated {@link Principal} set by
 * the inbound JWT interceptor (WebSocketConfig#configureClientInboundChannel);
 * client-supplied user fields in the payload are never trusted.
 *
 * <p>Kept deliberately separate from the REST {@link ChatController}: this class
 * is annotated {@link org.springframework.stereotype.Controller @Controller} for
 * messaging handlers, the REST controller stays untouched so MVC and messaging
 * concerns do not mix.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final MessageBroadcastService messageBroadcastService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle an incoming chat message from a STOMP SEND frame.
     * Persists via ChatService (DB + recent-message cache), then broadcasts.
     * On any failure the sender is notified on their /user/queue/errors queue.
     */
    @MessageMapping("/chat.send/{streamKey}")
    public void sendMessage(
            @DestinationVariable String streamKey,
            @Payload ChatMessageDTO payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated message attempt for stream {}", streamKey);
            return;
        }

        log.debug("Received message for stream {} from {}", streamKey, principal.getName());

        try {
            ChatMessageDTO message = chatService.sendMessage(
                    streamKey,
                    principal.getName(),
                    payload.getContent(),
                    MessageType.CHAT,
                    payload.getReplyToMessageId(),
                    payload.getIdempotencyKey()
            );

            messageBroadcastService.broadcastMessage(streamKey, message, false);

        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);

            ChatMessageDTO errorMessage = ChatMessageDTO.builder()
                    .messageType(MessageType.ERROR)
                    .content(e.getMessage())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    errorMessage
            );
        }
    }

    /**
     * Handle a user joining a chat. Emits a JOIN presence event.
     */
    @MessageMapping("/chat.join/{streamKey}")
    public void userJoin(
            @DestinationVariable String streamKey,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated join attempt for stream {}", streamKey);
            return;
        }

        log.info("User joined chat: stream={}, user={}", streamKey, principal.getName());

        ChatMessageDTO joinMessage = ChatMessageDTO.builder()
                .username(principal.getName())
                .content(principal.getName() + " joined the chat")
                .messageType(MessageType.JOIN)
                .build();

        messageBroadcastService.broadcastEvent(streamKey, joinMessage);
    }

    /**
     * Handle a user leaving a chat. Emits a LEAVE presence event.
     */
    @MessageMapping("/chat.leave/{streamKey}")
    public void userLeave(
            @DestinationVariable String streamKey,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated leave attempt for stream {}", streamKey);
            return;
        }

        log.info("User left chat: stream={}, user={}", streamKey, principal.getName());

        ChatMessageDTO leaveMessage = ChatMessageDTO.builder()
                .username(principal.getName())
                .content(principal.getName() + " left the chat")
                .messageType(MessageType.LEAVE)
                .build();

        messageBroadcastService.broadcastEvent(streamKey, leaveMessage);
    }
}