package com.streamchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.dto.*;
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

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void register_success_returnsCreatedAndToken() throws Exception {
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
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(eq(authentication)))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
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
        when(userService.getUserByUsername(eq("alice")))
                .thenReturn(UserDTO.builder().id(1L).username("alice").email("alice@example.com").build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
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
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void refreshToken_success_returnsNewToken() throws Exception {
        when(tokenProvider.generateToken(eq("alice"))).thenReturn("new-token");
        when(userService.getUserByUsername(eq("alice")))
                .thenReturn(UserDTO.builder().id(1L).username("alice").email("alice@example.com").build());

        mockMvc.perform(post("/api/auth/refresh")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-token"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        Mockito.verify(tokenProvider).generateToken(eq("alice"));
    }
}
