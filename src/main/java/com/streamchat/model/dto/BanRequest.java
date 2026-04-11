package com.streamchat.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for ban moderation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @Builder.Default
    private Boolean permanent = true;

    @Min(value = 1, message = "Duration seconds must be positive for temporary bans")
    private Integer durationSeconds;

    private String reason;
}
