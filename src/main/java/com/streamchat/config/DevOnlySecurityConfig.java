package com.streamchat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dev-only security rules for the H2 console.
 * The H2 console is only ever reachable under the "dev" profile;
 * the production SecurityConfig must contain no reference to it.
 */
@Configuration
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevOnlySecurityConfig {

    @Bean
    public SecurityFilterChain devOnlySecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                );
        return http.build();
    }
}