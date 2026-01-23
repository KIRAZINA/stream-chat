package com.streamchat.model.dto;

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

    @Builder.Default
    private String type = "Bearer";

    private String username;
    private String email;
}