package com.streamchat.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for chat features: history, moderation, settings enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ChatFeaturesIntegrationTest {

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
    private StreamSettingsRepository streamSettingsRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserStreamRoleRepository userStreamRoleRepository;

    @BeforeEach
    void cleanup() {
        chatMessageRepository.deleteAll();
        userStreamRoleRepository.deleteAll();
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void chatHistory_pagination_works() throws Exception {
        String token = registerAndLogin("broadcaster", "broadcaster@example.com", "password123");
        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        String streamKey = createStream(token, "Test Stream");

        // Empty history should return empty list
        mockMvc.perform(get("/api/streams/" + streamKey + "/messages")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void chatHistory_cursorPagination_returnsCorrectResults() throws Exception {
        String token = registerAndLogin("broadcaster", "broadcaster@example.com", "password123");
        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        String streamKey = createStream(token, "Cursor Test");
        Stream stream = streamRepository.findByStreamKey(streamKey).orElseThrow();

        // History with cursor should work (even if empty)
        mockMvc.perform(get("/api/streams/" + streamKey + "/messages")
                        .param("before", "999")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void settings_enforcement_slowMode_configured() throws Exception {
        String token = registerAndLogin("broadcaster", "slow@example.com", "password123");
        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        String streamKey = createStream(token, "Slow Mode Test");
        Stream stream = streamRepository.findByStreamKey(streamKey).orElseThrow();

        // Enable slow mode
        mockMvc.perform(put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "slowModeEnabled", true,
                                "slowModeSeconds", 5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slowModeEnabled").value(true))
                .andExpect(jsonPath("$.slowModeSeconds").value(5));

        // Verify settings persisted
        mockMvc.perform(get("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slowModeEnabled").value(true));
    }

    @Test
    void settings_enforcement_subscribersOnlyMode_configured() throws Exception {
        String token = registerAndLogin("broadcaster", "sub@example.com", "password123");
        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        String streamKey = createStream(token, "Sub Only Test");

        // Enable subscribers-only mode
        mockMvc.perform(put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscribersOnlyMode", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribersOnlyMode").value(true));
    }

    @Test
    void moderation_deleteMessage_requiresValidMessageId() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod2@example.com", "password123");

        // Delete non-existent message should return error (4xx or 5xx)
        mockMvc.perform(delete("/api/streams/stream-abc/moderate/messages/99999")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void moderation_bulkDeleteMessages_returnsDeletedCount() throws Exception {
        String token = registerLoginAndMakeModerator("mod", "mod3@example.com", "password123");
        Stream stream = streamRepository.findByStreamKey("stream-abc").orElseThrow();

        // Bulk delete should return success with deletedCount
        mockMvc.perform(delete("/api/streams/stream-abc/moderate/messages/user/999")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.deletedCount").exists());
    }

    @Test
    void dto_validation_streamRequest_rejectsInvalid() throws Exception {
        String token = registerAndLogin("broadcaster", "valid@example.com", "password123");
        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        // Empty title should fail
        mockMvc.perform(post("/api/streams")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "",
                                "description", "Test"
                        ))))
                .andExpect(status().isBadRequest());

        // Title too long should fail
        mockMvc.perform(post("/api/streams")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "A".repeat(201),
                                "description", "Test"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dto_validation_settingsRequest_rejectsInvalid() throws Exception {
        String token = registerAndLogin("broadcaster", "settings@example.com", "password123");
        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        String streamKey = createStream(token, "Validation Test");

        // Negative slowModeSeconds should fail
        mockMvc.perform(put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "slowModeEnabled", true,
                                "slowModeSeconds", -1
                        ))))
                .andExpect(status().isBadRequest());
    }

    // Helper methods

    private String registerAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        return extractToken(body);
    }

    private String createStream(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/streams")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "description", "Test"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return body.split("\"streamKey\":\"")[1].split("\"")[0];
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

        Stream stream = streamRepository.findByStreamKey("stream-abc").orElseGet(() -> {
            User owner = userRepository.findByUsername(username).orElseThrow();
            Stream s = Stream.builder()
                    .streamKey("stream-abc")
                    .user(owner)
                    .isLive(true)
                    .build();
            return streamRepository.save(s);
        });

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
        return extractToken(body);
    }

    private String extractToken(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return json.get("token").asText();
        } catch (Exception e) {
            int start = body.indexOf("\"token\":\"") + 9;
            int end = body.indexOf("\"", start);
            if (start > 8 && end > start) {
                return body.substring(start, end);
            }
            return null;
        }
    }
}
