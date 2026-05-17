package com.streamchat.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StreamCreationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private StreamSettingsRepository streamSettingsRepository;

    @BeforeEach
    void cleanup() {
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createStream_withoutAuthentication_isRejectedBeforeController() throws Exception {
        mockMvc.perform(post("/api/streams")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "My first stream",
                                "category", "Music"
                        ))))
                .andExpect(status().is4xxClientError());

        assertThat(streamRepository.count()).isZero();
    }

    @Test
    void createStream_afterLogin_persistsStreamAndDefaultSettings() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String token = loginJson.get("token").asText();

        mockMvc.perform(post("/api/streams")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "My first stream",
                                "description", "Created from the dashboard",
                                "category", "Music"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streamKey").isNotEmpty())
                .andExpect(jsonPath("$.title").value("My first stream"))
                .andExpect(jsonPath("$.description").value("Created from the dashboard"))
                .andExpect(jsonPath("$.ownerUsername").value("alice"))
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        assertThat(streamRepository.count()).isEqualTo(1);
        assertThat(streamSettingsRepository.count()).isEqualTo(1);
    }
}
