package com.streamchat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for JWT token generation and validation.
 * Handles token creation, parsing, and expiration.
 * Uses JJWT 0.12.x API.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    /**
     * Minimum HMAC-SHA key size in bytes (256 bits).
     */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Secrets that are publicly known (committed to the repository, widely
     * copy-pasted from tutorials, or trivially guessable). These must never be
     * accepted by a production deployment.
     */
    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            "dev-secret-key-please-change-in-production-must-be-at-least-64-characters-long",
            "change-me",
            "changeme",
            "default-secret",
            "dev-secret",
            "jwt-secret",
            "jwtsecret",
            "password",
            "secret",
            "supersecret",
            "test-secret",
            "your-256-bit-secret",
            "your-secret-key"
    );

    /**
     * Profiles that are treated as production for secret validation purposes.
     */
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "docker");

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${jwt.access-token-ttl:}")
    private String accessTokenTtl;

    @Autowired
    private Environment env;

    private SecretKey key;
    private long accessTokenTtlMs;

    /**
     * Initialize the signing key after properties are loaded.
     * Fails fast when the secret is missing, too weak, or still set to a
     * publicly known default in a production deployment.
     */
    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be set and >= 256 bits (32+ bytes)");
        }

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and >= 256 bits (32+ bytes), current length: "
                            + secretBytes.length);
        }

        if (isProductionEnvironment() && isKnownWeakSecret(jwtSecret)) {
            log.error("CRITICAL SECURITY RISK: Default JWT secret detected in production environment. "
                    + "Application boot aborted.");
            throw new IllegalStateException(
                    "Default/weak JWT_SECRET detected in production environment. "
                            + "Set a strong, unique JWT_SECRET before starting the application.");
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
     * Production means an explicit prod/production/docker profile, or no
     * explicit profile at all (the safe default is to fail closed).
     */
    private boolean isProductionEnvironment() {
        if (env == null) {
            return true;
        }

        String[] activeProfiles = env.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return true;
        }

        return Arrays.stream(activeProfiles)
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains);
    }

    private boolean isKnownWeakSecret(String secret) {
        return secret != null
                && KNOWN_WEAK_SECRETS.contains(secret.trim().toLowerCase(Locale.ROOT));
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