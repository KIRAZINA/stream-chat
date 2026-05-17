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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
     * @return success message without authentication
     */
     @PostMapping("/register")
         public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
             log.info("Registration attempt: username={}", request.getUsername());
     
             try {
                 // Register user only (no auto-login)
                 UserDTO user = userService.registerUser(
                         request.getUsername(),
                         request.getEmail(),
                         request.getPassword()
                 );
     
                 log.info("User registered successfully: username={}", request.getUsername());
                 return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                         "message", "Registration successful. Please login.",
                         "username", user.getUsername()
                 ));
     
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
     
                  // Generate tokens
                  String accessToken = tokenProvider.generateToken(authentication);
                  String refreshToken = tokenProvider.generateToken(authentication.getName()); // Generate refresh token

                  // Get user details
                  UserDTO user = userService.getUserByUsername(request.getUsername());

                  AuthResponse response = AuthResponse.builder()
                          .token(accessToken)
                          .refreshToken(refreshToken) // Set refresh token
                          .type("Bearer")
                          .username(user.getUsername())
                          .email(user.getEmail())
                          .expiresIn(tokenProvider.getExpirationMs() / 1000) // Time in seconds
                          .build();
     
                 log.info("User logged in successfully: username={}", request.getUsername());
                 return ResponseEntity.ok(response);
     
             } catch (Exception e) {
                 log.error("Login failed: {}", e.getMessage());
                 throw new BadCredentialsException("Invalid username or password");
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
                .refreshToken(token) // In production, use separate refresh token
                .username(user.getUsername())
                .email(user.getEmail())
                .expiresIn(tokenProvider.getExpirationMs() / 1000)
                .build();

        return ResponseEntity.ok(response);
    }
}
