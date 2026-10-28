package com.streamchat.config;

import com.streamchat.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security configuration for JWT-based authentication.
 * Configures stateless session management and endpoint access rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String WILDCARD = "*";

    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "docker");

    private static final List<String> DEV_FALLBACK_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://127.0.0.1:5173"
    );

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
    );

    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
    );

    private static final List<String> EXPOSED_HEADERS = List.of(
            "Authorization", "Link", "X-Total-Count"
    );

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Autowired
    private Environment env;

    /**
     * Configure HTTP security with JWT authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/static/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/chat-test.html",
                                "/chat.html",
                                "/api/auth/**",
                                "/ws-chat/**",
"/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
.requestMatchers(HttpMethod.GET,
                                "/api/streams",
                                "/api/streams/*",
                                "/api/streams/*/messages",
                                "/api/streams/*/messages/replay",
                                "/api/streams/*/presence",
                                "/api/streams/*/emotes",
                                "/api/emotes/global"
                        ).permitAll()

                        // Authenticated endpoints
                        .requestMatchers(HttpMethod.POST, "/api/streams")
                        .authenticated()
                        .requestMatchers("/api/streams/*/moderate/**")
                        .authenticated()
                        .requestMatchers("/api/streams/*/settings")
                        .authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configure CORS to allow WebSocket and API access from the frontend.
     *
     * Production profiles (prod/docker, or no explicit profile) must provide an
     * explicit non-wildcard origin list. Only the dev profile may fall back to
     * local origins or opt into a wildcard (without credentials).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = parseOrigins(allowedOrigins);

        if (isDevProfile()) {
            if (origins.isEmpty()) {
                origins = DEV_FALLBACK_ORIGINS;
            }
            if (origins.contains(WILDCARD)) {
                // Wildcards are only valid without credentials; browsers reject
                // Access-Control-Allow-Origin: * together with credentials.
                configuration.setAllowedOriginPatterns(List.of(WILDCARD));
                configuration.setAllowCredentials(false);
            } else {
                configuration.setAllowedOrigins(origins);
                configuration.setAllowCredentials(true);
            }
        } else {
            if (origins.isEmpty() || origins.contains(WILDCARD)) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY RISK: Wildcard or empty CORS origins ('*') are strictly "
                                + "prohibited in production profiles.");
            }
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(true);
        }

        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseOrigins(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * Only an explicit dev profile is relaxed. Production profiles and unknown
     * profiles (including no active profile) fail closed.
     */
    private boolean isDevProfile() {
        if (env == null) {
            return false;
        }

        String[] activeProfiles = env.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }

        boolean hasProductionProfile = Arrays.stream(activeProfiles)
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains);
        if (hasProductionProfile) {
            return false;
        }

        return Arrays.stream(activeProfiles)
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch("dev"::equals);
    }

    /**
     * Password encoder for hashing user passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication manager for handling authentication requests.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
