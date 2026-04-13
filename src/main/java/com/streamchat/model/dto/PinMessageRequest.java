package com.streamchat.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request to pin or unpin a message.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinMessageRequest {

    @NotNull(message = "Message ID is required")
    private Long messageId;

    @Builder.Default
    private Boolean pin = true;
}
