package com.streamchat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Utility class for JWT token generation and validation.
 * Handles token creation, parsing, and expiration.
 * Uses JJWT 0.12.x API.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${jwt.access-token-ttl:}")
    private String accessTokenTtl;

    private SecretKey key;
    private long accessTokenTtlMs;

    /**
     * Initialize the signing key after properties are loaded.
     * Fails fast when the secret is missing or too weak so a
     * deployment with a known/weak secret can never start.
     */
    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set and >= 256 bits");
        }
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and >= 256 bits (32+ bytes), current length: "
                            + secretBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);

        if (StringUtils.hasText(accessTokenTtl)) {
            this.accessTokenTtlMs = Duration.parse(accessTokenTtl).toMillis();
        } else {
            this.accessTokenTtlMs = jwtExpirationMs;
        }
        log.info("JWT access token TTL configured: {} ms", accessTokenTtlMs);
    }

    /**
     * Generate JWT token from authentication object.
     *
     * @param authentication the authenticated user
     * @return JWT token string
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails.getUsername());
    }

    /**
     * Generate JWT token from username.
     *
     * @param username the username
     * @return JWT token string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenTtlMs);

        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Get token expiration time in milliseconds.
     */
    public long getExpirationMs() {
        return accessTokenTtlMs;
    }

    /**
     * Extract username from JWT token.
     *
     * @param token the JWT token
     * @return username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * Extract the token identifier (jti claim) used for revocation.
     *
     * @param token the JWT token
     * @return the jti value
     */
    public String getTokenId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getId();
    }

    /**
     * Validate JWT token.
     *
     * @param token the JWT token
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}