package com.streamchat.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * DTO for authentication requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}