package com.streamchat.model.dto;

import lombok.*;

/**
 * Request DTO for refresh token rotation and logout.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    private String refreshToken;
}