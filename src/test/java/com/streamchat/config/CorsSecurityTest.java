package com.streamchat.config;

import com.streamchat.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Phase 1.2: CORS must fail closed in production-like profiles and only relax
 * to local origins (or an explicit dev wildcard without credentials) in dev.
 */
class CorsSecurityTest {

    private static final String PROD_ORIGIN = "https://streamchat.example.com";
    private static final String CRITICAL_CORS_ERROR =
            "CRITICAL SECURITY RISK: Wildcard or empty CORS origins ('*') are strictly "
                    + "prohibited in production profiles.";

    private static final List<String> DEV_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://127.0.0.1:5173"
    );

    @Test
    void prodProfile_rejectsWildcardOrigins() {
        SecurityConfig config = configWithOrigins("*", "prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                config::corsConfigurationSource);
        assertEquals(CRITICAL_CORS_ERROR, error.getMessage());
    }

    @Test
    void prodProfile_rejectsEmptyOrigins() {
        SecurityConfig config = configWithOrigins("", "prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                config::corsConfigurationSource);
        assertEquals(CRITICAL_CORS_ERROR, error.getMessage());
    }

    @Test
    void dockerProfile_rejectsWildcardOrigins() {
        SecurityConfig config = configWithOrigins("*", "docker");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                config::corsConfigurationSource);
        assertEquals(CRITICAL_CORS_ERROR, error.getMessage());
    }

    @Test
    void noActiveProfile_rejectsWildcardOrigins() {
        SecurityConfig config = configWithOrigins("*");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                config::corsConfigurationSource);
        assertEquals(CRITICAL_CORS_ERROR, error.getMessage());
    }

    @Test
    void prodProfile_acceptsExplicitOriginWithCredentialsAndExplicitCorsSettings() {
        CorsConfiguration cors = corsConfiguration(configWithOrigins(PROD_ORIGIN, "prod"));

        assertEquals(List.of(PROD_ORIGIN), cors.getAllowedOrigins());
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
        assertEquals(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"),
                cors.getAllowedMethods());
        assertEquals(List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Requested-With",
                        "Accept",
                        "Origin",
                        "Access-Control-Request-Method",
                        "Access-Control-Request-Headers"),
                cors.getAllowedHeaders());
        assertEquals(List.of("Authorization", "Link", "X-Total-Count"), cors.getExposedHeaders());
        assertEquals(3600L, cors.getMaxAge());
    }

    @Test
    void devProfile_parsesLocalhostOriginsAndAllowsCredentials() {
        CorsConfiguration cors = corsConfiguration(configWithOrigins(String.join(",", DEV_ORIGINS), "dev"));

        assertEquals(DEV_ORIGINS, cors.getAllowedOrigins());
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
        assertNull(cors.getAllowedOriginPatterns());
    }

    @Test
    void devProfile_fallsBackToLocalOriginsWhenUnset() {
        CorsConfiguration cors = corsConfiguration(configWithOrigins("", "dev"));

        assertEquals(DEV_ORIGINS, cors.getAllowedOrigins());
        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
    }

    @Test
    void devProfile_wildcardUsesPatternsWithoutCredentials() {
        CorsConfiguration cors = corsConfiguration(configWithOrigins("*", "dev"));

        assertEquals(List.of("*"), cors.getAllowedOriginPatterns());
        assertEquals(Boolean.FALSE, cors.getAllowCredentials());
        assertNull(cors.getAllowedOrigins());
    }

    @Test
    void devProfile_handlesPreflightOptionsRequestCleanly() throws Exception {
        CorsConfigurationSource source = configWithOrigins(String.join(",", DEV_ORIGINS), "dev")
                .corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/streams");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, (req, res) -> {
            // Preflight requests must be answered by the CORS filter, not the chain.
        });

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("http://localhost:5173",
                response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("POST"));
        assertFalse(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("TRACE"));
    }

    private static SecurityConfig configWithOrigins(String origins, String... activeProfiles) {
        SecurityConfig config = new SecurityConfig(mock(JwtAuthenticationFilter.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);

        MockEnvironment environment = new MockEnvironment();
        if (activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }
        ReflectionTestUtils.setField(config, "env", environment);
        return config;
    }

    private static CorsConfiguration corsConfiguration(SecurityConfig config) {
        CorsConfigurationSource source = config.corsConfigurationSource();
        UrlBasedCorsConfigurationSource urlSource = (UrlBasedCorsConfigurationSource) source;
        CorsConfiguration cors = urlSource.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/streams"));
        assertTrue(cors != null, "CORS configuration must be registered for /**");
        return cors;
    }
}
