package com.streamchat.config;

import com.streamchat.repository.StreamRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.StreamAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * 2.1: WebSocket allowed-origins validation must reject empty and wildcard
 * configurations at startup (fail fast) and parse explicit lists correctly.
 */
class WebSocketConfigTest {

    private WebSocketConfig config;

    @BeforeEach
    void setUp() {
        config = new WebSocketConfig(
                mock(JwtTokenProvider.class),
                mock(UserDetailsService.class),
                mock(StreamAuthorizationService.class),
                mock(StreamRepository.class));
    }

    private void setOrigins(String value) {
        ReflectionTestUtils.setField(config, "allowedOrigins", value);
    }

    @Test
    void parseOrigins_trimsAndSplitsExplicitList() {
        setOrigins(" http://localhost:5173 ,http://localhost:3000, https://chat.example.com ");
        assertEquals(List.of("http://localhost:5173", "http://localhost:3000", "https://chat.example.com"),
                config.parseOrigins());
    }

    @Test
    void validateAllowedOrigins_acceptsExplicitList() {
        setOrigins("http://localhost:5173,http://localhost:3000");
        assertDoesNotThrow(config::validateAllowedOrigins);
    }

    @Test
    void validateAllowedOrigins_rejectsWildcard() {
        setOrigins("*");
        assertThrows(IllegalStateException.class, config::validateAllowedOrigins);
    }

    @Test
    void validateAllowedOrigins_rejectsWildcardInsideList() {
        setOrigins("http://localhost:5173,*");
        assertThrows(IllegalStateException.class, config::validateAllowedOrigins);
    }

    @Test
    void validateAllowedOrigins_rejectsEmptyString() {
        setOrigins("");
        assertThrows(IllegalStateException.class, config::validateAllowedOrigins);
    }

    @Test
    void validateAllowedOrigins_rejectsNull() {
        setOrigins(null);
        assertThrows(IllegalStateException.class, config::validateAllowedOrigins);
    }

    @Test
    void validateAllowedOrigins_rejectsBlankString() {
        setOrigins("   ");
        assertThrows(IllegalStateException.class, config::validateAllowedOrigins);
    }
}