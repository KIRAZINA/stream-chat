package com.streamchat.model.dto;

import com.streamchat.model.enums.ModerationActionType;
import lombok.*;

/**
 * Data Transfer Object for moderation actions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationActionDTO {

    private ModerationActionType actionType;
    private String targetUsername;
    private Long messageId;
    private Integer durationSeconds;
    private String reason;
}