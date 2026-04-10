package com.streamchat.model.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for updating stream chat settings.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamSettingsUpdateRequest {

    private Boolean slowModeEnabled;

    @Min(value = 0, message = "Slow mode seconds must be non-negative")
    private Integer slowModeSeconds;

    private Boolean followersOnlyMode;

    @Min(value = 0, message = "Followers-only duration must be non-negative")
    private Integer followersOnlyDurationMinutes;

    private Boolean subscribersOnlyMode;

    private Boolean emoteOnlyMode;

    @Min(value = 1, message = "Max message length must be at least 1")
    private Integer maxMessageLength;

    private Boolean profanityFilterEnabled;

    private Boolean linkProtectionEnabled;
}
