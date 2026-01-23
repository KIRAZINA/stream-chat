package com.streamchat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.ModerationLogRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ModerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @MockBean
    private ModerationService moderationService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ModerationLogRepository moderationLogRepository;

    @MockBean
    private UserStreamRoleRepository userStreamRoleRepository;

    @BeforeEach
    void cleanup() {
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void moderator_can_delete_message() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod@example.com", "password123");

        mockMvc.perform(delete("/api/streams/stream-abc/moderate/messages/123")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(chatService).deleteMessage(eq(123L), eq("mod"));
    }

    @Test
    void moderator_can_timeout_user() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod@example.com", "password123");

        mockMvc.perform(post("/api/streams/stream-abc/moderate/timeout")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "target",
                                "durationSeconds", 60,
                                "reason", "spam"
                        ))))
                .andExpect(status().isOk());

        verify(moderationService).timeoutUser(eq(1L), eq(1L), eq(1L), eq(60), eq("spam"));
    }

    @Test
    void moderator_can_ban_user() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod@example.com", "password123");

        mockMvc.perform(post("/api/streams/stream-abc/moderate/ban")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "target",
                                "reason", "toxicity",
                                "permanent", true
                        ))))
                .andExpect(status().isOk());

        verify(moderationService).banUser(eq(1L), eq(1L), eq(1L), eq(true), isNull(), eq("toxicity"));
    }

    @Test
    void moderation_logs_endpoint_returns_200() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod@example.com", "password123");

        when(moderationLogRepository.findByStreamIdOrderByCreatedAtDesc(eq(1L)))
                .thenReturn(List.of(ModerationLog.builder()
                        .id(1L)
                        .streamId(1L)
                        .moderatorId(1L)
                        .targetUserId(1L)
                        .actionType(ModerationActionType.BAN)
                        .build()));

        mockMvc.perform(get("/api/streams/stream-abc/moderate/logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String registerLoginAndMakeModerator(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated());

        User u = userRepository.findByUsername(username).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(u).role(Role.ROLE_MODERATOR).build());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String body = login.getResponse().getContentAsString();
        return body.split("\"token\":\"")[1].split("\"", 2)[0];
    }
}
