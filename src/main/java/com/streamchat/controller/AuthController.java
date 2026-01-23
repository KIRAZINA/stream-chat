package com.streamchat.controller;

import com.streamchat.model.dto.*;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Handles user registration, login, and token management.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    /**
     * Register a new user.
     *
     * @param request registration details
     * @return authentication response with JWT token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt: username={}", request.getUsername());

        try {
            // Register user
            UserDTO user = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );

            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Generate JWT token
            String token = tokenProvider.generateToken(authentication);

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .build();

            log.info("User registered successfully: username={}", request.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Authenticate user and generate JWT token.
     *
     * @param request login credentials
     * @return authentication response with JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login attempt: username={}", request.getUsername());

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Generate JWT token
            String token = tokenProvider.generateToken(authentication);

            // Get user details
            UserDTO user = userService.getUserByUsername(request.getUsername());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .build();

            log.info("User logged in successfully: username={}", request.getUsername());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            throw new RuntimeException("Invalid username or password");
        }
    }

    /**
     * Refresh JWT token.
     *
     * @param authentication current authentication
     * @return new JWT token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(Authentication authentication) {
        log.info("Token refresh: username={}", authentication.getName());

        String token = tokenProvider.generateToken(authentication.getName());
        UserDTO user = userService.getUserByUsername(authentication.getName());

        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .build();

        return ResponseEntity.ok(response);
    }
}