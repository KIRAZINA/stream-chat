package com.streamchat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter for extracting and validating JWT tokens from requests.
 * Runs once per request to authenticate users.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String REVOKED_JWT_KEY = "revoked:jwt:";

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Filter incoming requests to extract and validate JWT tokens.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)
                    && !isRevoked(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);

                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Set authentication for user: {}", username);
                } catch (UsernameNotFoundException unfe) {
                    log.debug("User not found for username from token: {}", username);
                }
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check the revocation set. This check fails OPEN when Redis is
     * unavailable: access tokens are short-lived (minutes), so a
     * stale un-revoked token self-expires quickly. This is the
     * opposite of ban/timeout checks, which must fail closed.
     */
    private boolean isRevoked(String token) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String jti = tokenProvider.getTokenId(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_JWT_KEY + jti));
        } catch (Exception e) {
            log.debug("Redis unavailable for JWT revocation check; failing open: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract JWT token from Authorization header.
     *
     * @param request the HTTP request
     * @return JWT token or null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}