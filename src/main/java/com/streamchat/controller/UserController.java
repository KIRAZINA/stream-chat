package com.streamchat.controller;

import com.streamchat.model.dto.UserProfileDTO;
import com.streamchat.model.dto.UserStreamRoleDTO;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRoles(user.getRoles().stream().map(userRole -> userRole.getRole().toString()).toList());
        dto.setStreamRoles(user.getStreamRoles().stream().map(this::mapToRoleDTO).toList());

        return ResponseEntity.ok(dto);
    }

    private UserStreamRoleDTO mapToRoleDTO(UserStreamRole role) {
        return new UserStreamRoleDTO(role.getStream().getStreamKey(), role.getRole().toString());
    }
}