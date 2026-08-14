package com.streamchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.dto.*;
import com.streamchat.model.entity.RefreshToken;
import com.streamchat.model.entity.User;
import com.streamchat.repository.RefreshTokenRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"null", "NullAway"})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private UserService userService;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void register_success_returnsCreatedMessage() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("password123")
                .build();

        UserDTO user = UserDTO.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken("alice", "N/A");

        when(userService.registerUser(eq("alice"), eq("alice@example.com"), eq("password123")))
                .thenReturn(user);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful. Please login."))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void register_validationError_returnsBadRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("ab")
                .email("not-an-email")
                .password("123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_success_returnsToken() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("alice")
                .password("password123")
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken("alice", "N/A");

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(eq(authentication)))
                .thenReturn("jwt-token");
        when(userService.findByUsername(eq("alice")))
                .thenReturn(Optional.of(User.builder().id(1L).username("alice").email("alice@example.com").build()));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUserByUsername(eq("alice")))
                .thenReturn(UserDTO.builder().id(1L).username("alice").email("alice@example.com").build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void login_authenticationFails_returnsUnauthorized() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("alice")
                .password("wrong")
                .build();

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new RuntimeException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refreshToken_success_rotatesToken() throws Exception {
        User user = User.builder().id(1L).username("alice").email("alice@example.com").build();
        RefreshToken stored = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash("stored-hash")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenProvider.generateToken(eq("alice"))).thenReturn("new-token");
        when(userService.getUserByUsername(eq("alice")))
                .thenReturn(UserDTO.builder().id(1L).username("alice").email("alice@example.com").build());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"raw-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-token"))
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        Mockito.verify(tokenProvider).generateToken(eq("alice"));
    }

    @Test
    void refreshToken_reuse_revokesAllUserTokensAndReturns401() throws Exception {
        User user = User.builder().id(1L).username("alice").email("alice@example.com").build();
        RefreshToken reused = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash("reused-hash")
                .replacedBy(99L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(reused));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"stolen-token\"}"))
                .andExpect(status().isUnauthorized());

        Mockito.verify(refreshTokenRepository).revokeAllForUser(eq(1L));
    }

    @Test
    void refreshToken_invalid_returns401() throws Exception {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesPresentedRefreshToken() throws Exception {
        RefreshToken stored = RefreshToken.builder()
                .id(1L)
                .user(User.builder().id(1L).username("alice").build())
                .tokenHash("stored-hash")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"raw-token\"}"))
                .andExpect(status().isOk());

        Mockito.verify(refreshTokenRepository).save(stored);
    }
}
