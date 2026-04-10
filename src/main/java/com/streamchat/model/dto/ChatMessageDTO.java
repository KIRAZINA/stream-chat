package com.streamchat.model.dto;

import com.streamchat.model.dto.MessageFragmentDTO;
import com.streamchat.model.enums.MessageType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for chat messages.
 * Used for API responses and WebSocket messaging.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {

    private Long id;
    private Long streamId;
    private Long userId;
    private String username;
    private String content;
    private Long replyToMessageId;
    private String replyToUsername;
    private String replyToContentPreview;
    private MessageType messageType;
    private String color;
    private List<String> badges;
    private List<MessageFragmentDTO> fragments;
    private Boolean isDeleted;
    private Long deletedById;
    private String deletedByUsername;
    private LocalDateTime deletedAt;
    private LocalDateTime timestamp;
}