package com.streamchat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.RefreshTokenRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StreamHistoryAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private StreamSettingsRepository streamSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStreamRoleRepository userStreamRoleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        userStreamRoleRepository.deleteAll();
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void history_blocked_for_non_subscriber_when_subscribers_only() throws Exception {
        registerUser("owner", "owner@example.com", "password123");
        Stream stream = ensureStream("restricted-stream");
        streamSettingsRepository.save(StreamSettings.builder()
                .stream(stream)
                .subscribersOnlyMode(true)
                .build());

        registerUser("viewer", "viewer@example.com", "password123");
        String token = loginAndExtractToken("viewer", "password123");

        mockMvc.perform(get("/api/streams/restricted-stream/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_allowed_for_subscriber_when_subscribers_only() throws Exception {
        registerUser("owner", "owner@example.com", "password123");
        Stream stream = ensureStream("restricted-stream");
        streamSettingsRepository.save(StreamSettings.builder()
                .stream(stream)
                .subscribersOnlyMode(true)
                .build());

        registerUser("sub", "sub@example.com", "password123");
        User sub = userRepository.findByUsername("sub").orElseThrow();
        userStreamRoleRepository.save(UserStreamRole.builder()
                .user(sub)
                .stream(stream)
                .role(Role.ROLE_SUBSCRIBER)
                .build());
        String token = loginAndExtractToken("sub", "password123");

        mockMvc.perform(get("/api/streams/restricted-stream/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void history_allowed_anonymously_when_unrestricted() throws Exception {
        registerUser("owner", "owner@example.com", "password123");
        ensureStream("open-stream");

        mockMvc.perform(get("/api/streams/open-stream/messages"))
                .andExpect(status().isOk());
    }

    private void registerUser(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated());
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
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

    private Stream ensureStream(String streamKey) {
        User owner = userRepository.findByUsername("owner").orElseThrow();
        return streamRepository.save(Stream.builder()
                .streamKey(streamKey)
                .user(owner)
                .isLive(true)
                .build());
    }
}
