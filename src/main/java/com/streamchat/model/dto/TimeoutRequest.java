package com.streamchat.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for timeout moderation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeoutRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotNull(message = "Duration seconds is required")
    @Positive(message = "Duration seconds must be greater than zero")
    private Integer durationSeconds;

    private String reason;
}
