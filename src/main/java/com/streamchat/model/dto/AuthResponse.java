package com.streamchat.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * DTO for authentication responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @Builder.Default
    private String type = "Bearer";

    private String username;
    private String email;

    @JsonProperty("expires_in")
    private Long expiresIn;
}