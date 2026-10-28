package com.streamchat.controller;

import com.streamchat.exception.ErrorResponse;
import com.streamchat.exception.RateLimitException;
import com.streamchat.exception.ResourceNotFoundException;
import com.streamchat.exception.UnauthorizedException;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.service.ChatService;
import com.streamchat.service.MessageBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.AuthenticationException;
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

        String path = "/app/chat.send/" + streamKey;
        String idempotencyKey = payload != null ? payload.getIdempotencyKey() : null;

        if (principal == null) {
            log.warn("Unauthenticated message attempt for stream {}", streamKey);
            sendError(null, ErrorResponse.of(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "Authentication required",
                    path,
                    idempotencyKey));
            return;
        }

        if (payload == null) {
            log.warn("Empty message payload for stream {} from {}", streamKey, principal.getName());
            sendError(principal, ErrorResponse.of(
                    HttpStatus.BAD_REQUEST.value(),
                    "Bad Request",
                    "Message payload is required",
                    path,
                    null));
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
            sendError(principal, toErrorResponse(e, path, idempotencyKey));
        }
    }

    /**
     * Map a chat-processing exception onto the same status/error vocabulary the
     * REST layer uses, so clients can handle both transports uniformly.
     */
    static ErrorResponse toErrorResponse(Exception exception, String path, String idempotencyKey) {
        HttpStatus status;
        String error;
        String message;

        if (exception instanceof RateLimitException) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            error = "Rate Limit Exceeded";
            message = messageOrDefault(exception, "Rate limit exceeded");
        } else if (exception instanceof UnauthorizedException
                || exception instanceof AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
            error = "Unauthorized";
            message = messageOrDefault(exception, "Authentication required");
        } else if (exception instanceof ResourceNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            error = "Not Found";
            message = messageOrDefault(exception, "Resource not found");
        } else if (exception instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            error = "Bad Request";
            message = messageOrDefault(exception, "Invalid request");
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            error = "Internal Server Error";
            message = "An unexpected error occurred";
        }

        return ErrorResponse.of(status.value(), error, message, path, idempotencyKey);
    }

    private static String messageOrDefault(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    /**
     * Deliver an error frame to the sender's private queue. When there is no
     * authenticated principal (normally rejected during the handshake) the
     * user-destination path is used so the frame is still observable.
     */
    private void sendError(Principal principal, ErrorResponse error) {
        if (principal != null) {
            messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", error);
        } else {
            messagingTemplate.convertAndSend("/user/queue/errors", error);
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