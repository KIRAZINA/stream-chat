package com.streamchat.controller;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.service.ChatService;
import com.streamchat.service.MessageBroadcastService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final MessageBroadcastService messageBroadcastService;

    @PostMapping("/{streamKey}/messages")
    public ResponseEntity<ChatMessageDTO> sendMessage(
            @PathVariable String streamKey,
            @Valid @RequestBody SendMessageRequest request,
            Principal principal) {

        log.debug("Sending message to stream '{}' from user '{}'", streamKey, principal.getName());

        MessageType messageType = request.getMessageType() != null
                ? request.getMessageType() : MessageType.CHAT;

        ChatMessageDTO dto = chatService.sendMessage(
                streamKey,
                principal.getName(),
                request.getContent(),
                messageType,
                request.getReplyToMessageId(),
                request.getIdempotencyKey());

        // A REST send can land on ANY instance, so it must never use local-first
        // delivery: force the Redis fan-out path so all instances broadcast.
        messageBroadcastService.broadcastMessage(streamKey, dto, true);

        return ResponseEntity.ok(dto);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        @NotBlank(message = "Content must not be blank")
        @Size(max = 2000, message = "Content must not exceed 2000 characters")
        private String content;

        private MessageType messageType;
        private Long replyToMessageId;
        private String idempotencyKey;
    }
}
