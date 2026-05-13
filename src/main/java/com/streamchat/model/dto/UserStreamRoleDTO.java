package com.streamchat.model.dto;

import lombok.Data;

@Data
public class UserStreamRoleDTO {
    private String streamKey;
    private String role;

    public UserStreamRoleDTO(String streamKey, String role) {
        this.streamKey = streamKey;
        this.role = role;
    }
}