package com.streamchat.service;

import com.streamchat.model.dto.UserDTO;
import com.streamchat.model.entity.User;
import com.streamchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

/**
 * Service for managing users.
 * Handles user registration, authentication, and profile management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user.
     *
     * @param username the username
     * @param email the email
     * @param password the password
     * @return created user DTO
     */
    @Transactional
    public UserDTO registerUser(String username, String email, String password) {
        // Check if username exists
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }

        // Check if email exists
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        // Create user
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(username)
                .color(generateRandomColor())
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: username={}, email={}", username, email);

        return convertToDTO(saved);
    }

    /**
     * Get user by username.
     *
     * @param username the username
     * @return user DTO
     */
    public UserDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDTO(user);
    }

    /**
     * Update user display name.
     *
     * @param username the username
     * @param displayName the new display name
     */
    @Transactional
    public void updateDisplayName(String username, String displayName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setDisplayName(displayName);
        userRepository.save(user);

        log.info("Display name updated: username={}, newName={}", username, displayName);
    }

    /**
     * Update user color.
     *
     * @param username the username
     * @param color the new color hex code
     */
    @Transactional
    public void updateColor(String username, String color) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate color format
        if (!color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid color format");
        }

        user.setColor(color);
        userRepository.save(user);

        log.info("Color updated: username={}, newColor={}", username, color);
    }

    /**
     * Generate random color for new users.
     */
    private String generateRandomColor() {
        Random random = new Random();
        int r = random.nextInt(256);
        int g = random.nextInt(256);
        int b = random.nextInt(256);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /**
     * Convert entity to DTO.
     */
    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .color(user.getColor())
                .createdAt(user.getCreatedAt())
                .build();
    }
}