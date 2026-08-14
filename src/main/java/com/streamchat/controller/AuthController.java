package com.streamchat.controller;

import com.streamchat.model.dto.*;
import com.streamchat.model.entity.RefreshToken;
import com.streamchat.model.entity.User;
import com.streamchat.repository.RefreshTokenRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

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
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-ttl:P30D}")
    private Duration refreshTokenTtl;

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
     * Authenticate user and generate an access token and an
     * opaque refresh token.
     *
     * @param request login credentials
     * @return authentication response with access + refresh tokens
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
     
                 // Access token is short-lived; refresh token is opaque and rotated
                 String accessToken = tokenProvider.generateToken(authentication);
                 User user = userService.findByUsername(request.getUsername())
                         .orElseThrow(() -> new RuntimeException("User not found"));
                 String refreshToken = issueRefreshToken(user);
     
                 // Get user details
                 UserDTO userDTO = userService.getUserByUsername(request.getUsername());
     
                 AuthResponse response = AuthResponse.builder()
                         .token(accessToken)
                         .refreshToken(refreshToken)
                         .type("Bearer")
                         .username(userDTO.getUsername())
                         .email(userDTO.getEmail())
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
     * Rotate a refresh token. Reuse of an already-rotated token is
     * treated as theft: the entire token chain for the user is revoked.
     *
     * @param request the presented refresh token
     * @return a new access + refresh token pair
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request) {

        String rawToken = request != null ? request.getRefreshToken() : null;
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is required");
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.getReplacedBy() != null) {
            log.warn("Refresh token reuse detected for user {}: revoking all tokens",
                    stored.getUser().getUsername());
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw new BadCredentialsException("Refresh token has been reused");
        }
        if (stored.getRevokedAt() != null) {
            throw new BadCredentialsException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token has expired");
        }

        String newRawToken = UUID.randomUUID().toString();
        RefreshToken newToken = RefreshToken.builder()
                .user(stored.getUser())
                .tokenHash(sha256Hex(newRawToken))
                .expiresAt(LocalDateTime.now().plus(refreshTokenTtl))
                .build();
        newToken = refreshTokenRepository.save(newToken);

        stored.setRevokedAt(LocalDateTime.now());
        stored.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(stored);

        String accessToken = tokenProvider.generateToken(stored.getUser().getUsername());
        UserDTO user = userService.getUserByUsername(stored.getUser().getUsername());

        log.info("Refresh token rotated for user: {}", user.getUsername());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(accessToken)
                .refreshToken(newRawToken)
                .type("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .expiresIn(tokenProvider.getExpirationMs() / 1000)
                .build());
    }

    /**
     * Revoke the presented refresh token.
     *
     * @param request the refresh token to revoke
     * @return success response
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequest request) {

        if (request != null && request.getRefreshToken() != null
                && !request.getRefreshToken().isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256Hex(request.getRefreshToken()))
                    .ifPresent(token -> {
                        token.setRevokedAt(LocalDateTime.now());
                        refreshTokenRepository.save(token);
                    });
        }

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    private String issueRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plus(refreshTokenTtl))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}