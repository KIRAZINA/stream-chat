package com.streamchat.model.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for users.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String color;
    private LocalDateTime createdAt;
}