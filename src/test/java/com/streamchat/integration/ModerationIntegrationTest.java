package com.streamchat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.ModerationLogRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import com.streamchat.repository.AuditLogRepository;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.service.AuditService;
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

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private UserStreamRoleRepository userStreamRoleRepository;

    @MockBean
    private ModerationService moderationService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private ModerationLogRepository moderationLogRepository;

    @BeforeEach
    void cleanup() {
        userStreamRoleRepository.deleteAll();
        streamRepository.deleteAll();
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

        User target = ensureUserExists("target", "target@example.com");

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

        Long streamId = streamRepository.findByStreamKey("stream-abc").orElseThrow().getId();
        Long moderatorId = userRepository.findByUsername("mod").orElseThrow().getId();
        verify(moderationService).timeoutUser(eq(streamId), eq(target.getId()), eq(moderatorId), eq(60), eq("spam"));
    }

    @Test
    void moderator_can_ban_user() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod@example.com", "password123");

        User target = ensureUserExists("target", "target@example.com");

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

        Long streamId = streamRepository.findByStreamKey("stream-abc").orElseThrow().getId();
        Long moderatorId = userRepository.findByUsername("mod").orElseThrow().getId();
        verify(moderationService).banUser(eq(streamId), eq(target.getId()), eq(moderatorId), eq(true), isNull(), eq("toxicity"));
    }

    @Test
    void moderation_logs_endpoint_returns_200() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod@example.com", "password123");

        Long streamId = streamRepository.findByStreamKey("stream-abc").orElseThrow().getId();
        when(moderationLogRepository.findByStreamIdOrderByCreatedAtDesc(eq(streamId)))
                .thenReturn(List.of(ModerationLog.builder()
                        .id(1L)
                        .streamId(streamId)
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

        Stream stream = ensureStreamExists("stream-abc");
        User user = userRepository.findByUsername(username).orElseThrow();

        userStreamRoleRepository.save(UserStreamRole.builder()
                .user(user)
                .stream(stream)
                .role(Role.ROLE_MODERATOR)
                .build());

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

    private Stream ensureStreamExists(String streamKey) {
        return streamRepository.findByStreamKey(streamKey).orElseGet(() -> {
            User owner = ensureUserExists("owner", "owner@example.com");
            Stream stream = Stream.builder()
                    .streamKey(streamKey)
                    .user(owner)
                    .isLive(true)
                    .build();
            return streamRepository.save(stream);
        });
    }

    private User ensureUserExists(String username, String email) {
        return userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash("encoded")
                        .displayName(username)
                        .isActive(true)
                        .build()));
    }
}
