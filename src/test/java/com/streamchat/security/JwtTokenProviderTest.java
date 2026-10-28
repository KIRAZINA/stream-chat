package com.streamchat.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup validation for JWT_SECRET: weak/missing secrets must fail fast,
 * known public defaults must never be accepted in production-like profiles,
 * and legitimate strong secrets must keep working.
 */
class JwtTokenProviderTest {

    private static final String KNOWN_DEV_SECRET =
            "dev-secret-key-please-change-in-production-must-be-at-least-64-characters-long";
    private static final String STRONG_SECRET = "unit-test-strong-secret-0123456789abcdef";
    private static final String SHORT_SECRET = "only-31-bytes-long-secret-12345";

    @Test
    void init_failsWhenSecretIsNull() {
        JwtTokenProvider provider = providerWithSecret(null, "dev");

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(error.getMessage().contains("JWT_SECRET must be set"));
    }

    @Test
    void init_failsWhenSecretIsBlank() {
        JwtTokenProvider provider = providerWithSecret("   ", "prod");

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(error.getMessage().contains("JWT_SECRET must be set"));
    }

    @Test
    void init_failsWhenSecretIsShorterThan32Bytes() {
        JwtTokenProvider provider = providerWithSecret(SHORT_SECRET, "prod");

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(error.getMessage().contains(">= 256 bits"));
    }

    @Test
    void init_failsInProdProfileWhenSecretIsKnownDefault() {
        JwtTokenProvider provider = providerWithSecret(KNOWN_DEV_SECRET, "prod");

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(error.getMessage().contains("Default/weak JWT_SECRET"));
    }

    @Test
    void init_failsInDockerProfileWhenSecretIsKnownDefault() {
        JwtTokenProvider provider = providerWithSecret(KNOWN_DEV_SECRET, "docker");

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(error.getMessage().contains("Default/weak JWT_SECRET"));
    }

    @Test
    void init_failsWhenNoProfileIsActiveAndSecretIsKnownDefault() {
        JwtTokenProvider provider = providerWithSecret(KNOWN_DEV_SECRET);

        IllegalStateException error = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(error.getMessage().contains("Default/weak JWT_SECRET"));
    }

    @Test
    void init_allowsKnownDefaultOnlyInDevProfile() {
        JwtTokenProvider provider = providerWithSecret(KNOWN_DEV_SECRET, "dev");

        assertDoesNotThrow(provider::init);
    }

    @Test
    void init_succeedsWithStrongSecretInProdProfile() {
        JwtTokenProvider provider = providerWithSecret(STRONG_SECRET, "prod");

        assertDoesNotThrow(provider::init);
    }

    @Test
    void strongSecretInProdProfileStillGeneratesAndValidatesTokens() {
        JwtTokenProvider provider = providerWithSecret(STRONG_SECRET, "prod");
        provider.init();

        String token = provider.generateToken("alice");

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals("alice", provider.getUsernameFromToken(token));
        assertNotNull(provider.getTokenId(token));
    }

    private static JwtTokenProvider providerWithSecret(String secret, String... activeProfiles) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 86_400_000L);
        ReflectionTestUtils.setField(provider, "accessTokenTtl", "");

        MockEnvironment environment = new MockEnvironment();
        if (activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }
        ReflectionTestUtils.setField(provider, "env", environment);

        return provider;
    }
}
