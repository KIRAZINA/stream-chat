package com.streamchat.controller;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.dto.ModerationActionDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import com.streamchat.service.StreamAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final StreamAuthorizationService streamAuthorizationService;

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

        if (principal == null) {
            log.warn("Unauthenticated message attempt for stream {}", streamKey);
            return null;
        }

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

        if (principal == null) {
            log.warn("Unauthenticated join attempt for stream {}", streamKey);
            return null;
        }

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

        if (principal == null) {
            log.warn("Unauthenticated leave attempt for stream {}", streamKey);
            return null;
        }

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

        if (principal == null) {
            log.warn("Unauthenticated moderation attempt for stream {}", streamKey);
            return;
        }

        log.info("Moderation action: stream={}, moderator={}, action={}, target={}",
                streamKey, principal.getName(), action.getActionType(), action.getTargetUsername());

        try {
            if (!streamAuthorizationService.canModerate(streamKey, principal.getName())) {
                throw new com.streamchat.exception.UnauthorizedException("Insufficient permissions");
            }

            Stream stream = streamRepository.findByStreamKey(streamKey)
                    .orElseThrow(() -> new RuntimeException("Stream not found"));
            User moderator = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Moderator not found"));

            Long streamId = stream.getId();
            Long moderatorId = moderator.getId();
            Long userId = null;
            if (action.getTargetUsername() != null) {
                User targetUser = userRepository.findByUsername(action.getTargetUsername())
                        .orElseThrow(() -> new RuntimeException("User not found: " + action.getTargetUsername()));
                userId = targetUser.getId();
            }

            switch (action.getActionType()) {
                case TIMEOUT:
                    if (userId == null || action.getDurationSeconds() == null) {
                        throw new IllegalArgumentException("Target user and duration are required");
                    }
                    moderationService.timeoutUser(
                            streamId,
                            userId,
                            moderatorId,
                            action.getDurationSeconds(),
                            action.getReason()
                    );
                    break;

                case BAN:
                    if (userId == null) {
                        throw new IllegalArgumentException("Target user is required");
                    }
                    boolean isPermanent = action.getDurationSeconds() == null;
                    moderationService.banUser(
                            streamId,
                            userId,
                            moderatorId,
                            isPermanent,
                            action.getDurationSeconds(),
                            action.getReason()
                    );
                    break;

                case UNBAN:
                    if (userId == null) {
                        throw new IllegalArgumentException("Target user is required");
                    }
                    moderationService.unbanUser(streamId, userId, moderatorId);
                    break;

                case DELETE_MESSAGE:
                    if (action.getMessageId() == null) {
                        throw new IllegalArgumentException("Message ID is required");
                    }
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
