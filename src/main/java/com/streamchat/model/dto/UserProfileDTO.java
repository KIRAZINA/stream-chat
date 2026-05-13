package com.streamchat.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileDTO {
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
    private List<UserStreamRoleDTO> streamRoles;
}