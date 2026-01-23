package com.streamchat.controller;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.dto.ModerationActionDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket controller for handling real-time chat operations.
 * Manages message sending, user join/leave events, and moderation actions.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ModerationService moderationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle incoming chat messages.
     * Messages are validated, saved, and broadcast to all connected clients.
     *
     * @param streamKey the stream identifier
     * @param payload the message content
     * @param principal the authenticated user
     * @return the saved message
     */
    @MessageMapping("/chat.send/{streamKey}")
    @SendTo("/topic/stream/{streamKey}")
    public ChatMessageDTO sendMessage(
            @DestinationVariable String streamKey,
            @Payload ChatMessageDTO payload,
            Principal principal) {

        log.debug("Received message for stream {}: {}", streamKey, payload.getContent());

        try {
            // Process and save message
            ChatMessageDTO message = chatService.sendMessage(
                    streamKey,
                    principal.getName(),
                    payload.getContent(),
                    MessageType.CHAT
            );

            log.info("Message sent: stream={}, user={}, messageId={}",
                    streamKey, principal.getName(), message.getId());

            return message;

        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);

            // Send error message back to user
            ChatMessageDTO errorMessage = ChatMessageDTO.builder()
                    .messageType(MessageType.ERROR)
                    .content(e.getMessage())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    errorMessage
            );

            return null;
        }
    }

    /**
     * Handle user joining a chat.
     *
     * @param streamKey the stream identifier
     * @param principal the authenticated user
     */
    @MessageMapping("/chat.join/{streamKey}")
    @SendTo("/topic/stream/{streamKey}/events")
    public ChatMessageDTO userJoin(
            @DestinationVariable String streamKey,
            Principal principal) {

        log.info("User joined chat: stream={}, user={}", streamKey, principal.getName());

        ChatMessageDTO joinMessage = ChatMessageDTO.builder()
                .username(principal.getName())
                .content(principal.getName() + " joined the chat")
                .messageType(MessageType.JOIN)
                .build();

        return joinMessage;
    }

    /**
     * Handle user leaving a chat.
     *
     * @param streamKey the stream identifier
     * @param principal the authenticated user
     */
    @MessageMapping("/chat.leave/{streamKey}")
    @SendTo("/topic/stream/{streamKey}/events")
    public ChatMessageDTO userLeave(
            @DestinationVariable String streamKey,
            Principal principal) {

        log.info("User left chat: stream={}, user={}", streamKey, principal.getName());

        ChatMessageDTO leaveMessage = ChatMessageDTO.builder()
                .username(principal.getName())
                .content(principal.getName() + " left the chat")
                .messageType(MessageType.LEAVE)
                .build();

        return leaveMessage;
    }

    /**
     * Handle moderation actions (timeout, ban, delete message).
     *
     * @param streamKey the stream identifier
     * @param action the moderation action
     * @param principal the authenticated moderator
     */
    @MessageMapping("/chat.moderate/{streamKey}")
    public void moderateUser(
            @DestinationVariable String streamKey,
            @Payload ModerationActionDTO action,
            Principal principal) {

        log.info("Moderation action: stream={}, moderator={}, action={}, target={}",
                streamKey, principal.getName(), action.getActionType(), action.getTargetUsername());

        try {
            // TODO: Get stream and user IDs from service
            Long streamId = 1L; // Placeholder
            Long userId = 1L; // Placeholder
            Long moderatorId = 1L; // Placeholder

            switch (action.getActionType()) {
                case TIMEOUT:
                    moderationService.timeoutUser(
                            streamId,
                            userId,
                            moderatorId,
                            action.getDurationSeconds(),
                            action.getReason()
                    );
                    break;

                case BAN:
                    moderationService.banUser(
                            streamId,
                            userId,
                            moderatorId,
                            true,
                            null,
                            action.getReason()
                    );
                    break;

                case UNBAN:
                    moderationService.unbanUser(streamId, userId, moderatorId);
                    break;

                case DELETE_MESSAGE:
                    chatService.deleteMessage(action.getMessageId(), principal.getName());
                    break;

                default:
                    log.warn("Unknown moderation action type: {}", action.getActionType());
                    return;
            }

            // Broadcast moderation action to all clients
            messagingTemplate.convertAndSend(
                    "/topic/stream/" + streamKey + "/moderation",
                    action
            );

        } catch (Exception e) {
            log.error("Error performing moderation action: {}", e.getMessage(), e);

            // Send error back to moderator
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Moderation action failed: " + e.getMessage()
            );
        }
    }
}