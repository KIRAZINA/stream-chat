package com.streamchat.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
import com.streamchat.model.enums.Role;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StreamSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private StreamSettingsRepository streamSettingsRepository;

    @Autowired
    private UserStreamRoleRepository userStreamRoleRepository;

    @BeforeEach
    void cleanup() {
        streamSettingsRepository.deleteAll();
        userStreamRoleRepository.deleteAll();
        streamRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void broadcaster_can_create_stream_and_update_settings() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"broadcaster\",\"email\":\"broadcaster@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        User u = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(u).role(Role.ROLE_BROADCASTER).build());

        MvcResult login = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"broadcaster\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = login.getResponse().getContentAsString().split("\"token\":\"")[1].split("\",\"")[0];

        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/api/streams")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"My Stream\",\"description\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.streamKey").exists())
                .andReturn();

        String streamKey = created.getResponse().getContentAsString().split("\"streamKey\":\"")[1].split("\",\"")[0];

        MvcResult updated = mockMvc.perform(MockMvcRequestBuilders.put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slowModeEnabled\":true,\"slowModeSeconds\":3,\"maxMessageLength\":250}"))
                .andExpect(status().isOk())
                .andReturn();

        String updatedBody = updated.getResponse().getContentAsString();
        assertTrue(updatedBody.contains("\"slowModeEnabled\":true"), () -> "Unexpected response body: " + updatedBody);
        assertTrue(updatedBody.contains("\"slowModeSeconds\":3"), () -> "Unexpected response body: " + updatedBody);
        assertTrue(updatedBody.contains("\"maxMessageLength\":250"), () -> "Unexpected response body: " + updatedBody);

        MvcResult got = mockMvc.perform(MockMvcRequestBuilders.get("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String gotBody = got.getResponse().getContentAsString();
        assertTrue(gotBody.contains("\"slowModeEnabled\":true"), () -> "Unexpected response body: " + gotBody);
        assertTrue(gotBody.contains("\"slowModeSeconds\":3"), () -> "Unexpected response body: " + gotBody);
        assertTrue(gotBody.contains("\"maxMessageLength\":250"), () -> "Unexpected response body: " + gotBody);
    }
}
