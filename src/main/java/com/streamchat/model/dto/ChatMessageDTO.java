package com.streamchat.model.dto;

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
    private MessageType messageType;
    private String color;
    private List<String> badges;
    private LocalDateTime timestamp;
}