package com.streamchat.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserRole;
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

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void cleanup() {
        chatMessageRepository.deleteAll();
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

    @Test
    void saved_restrictions_are_enforced_for_non_privileged_users() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"broadcaster\",\"email\":\"broadcaster@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        User broadcaster = userRepository.findByUsername("broadcaster").orElseThrow();
        userRoleRepository.save(UserRole.builder().user(broadcaster).role(Role.ROLE_BROADCASTER).build());

        String token = loginAndExtractToken("broadcaster", "password123");

        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/api/streams")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"My Stream\",\"description\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String streamKey = created.getResponse().getContentAsString().split("\"streamKey\":\"")[1].split("\",\"")[0];

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"email\":\"viewer@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        String viewerToken = loginAndExtractToken("viewer", "password123");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscribersOnlyMode\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + viewerToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello from viewer\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello from broadcaster\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void slow_mode_blocks_rapid_messages_from_viewer() throws Exception {
        String streamKey = registerBroadcasterAndCreateStream("broadcaster", "broadcaster@example.com");
        String token = loginAndExtractToken("broadcaster", "password123");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"email\":\"viewer@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        String viewerToken = loginAndExtractToken("viewer", "password123");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slowModeEnabled\":true,\"slowModeSeconds\":10}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + viewerToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"first message\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + viewerToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"second message too soon\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void max_message_length_blocks_long_messages_for_everyone() throws Exception {
        String streamKey = registerBroadcasterAndCreateStream("broadcaster", "broadcaster@example.com");
        String token = loginAndExtractToken("broadcaster", "password123");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMessageLength\":5}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello world\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void slow_mode_applies_to_the_broadcaster_too() throws Exception {
        String streamKey = registerBroadcasterAndCreateStream("broadcaster", "broadcaster@example.com");
        String token = loginAndExtractToken("broadcaster", "password123");

        mockMvc.perform(MockMvcRequestBuilders.put("/api/streams/" + streamKey + "/settings")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slowModeEnabled\":true,\"slowModeSeconds\":10}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"first message\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/streams/" + streamKey + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"second message too soon\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private String registerBroadcasterAndCreateStream(String username, String email) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        User u = userRepository.findByUsername(username).orElseThrow();
        userRoleRepository.save(UserRole.builder().user(u).role(Role.ROLE_BROADCASTER).build());

        String token = loginAndExtractToken(username, "password123");

        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/api/streams")
                        .header("Authorization", "Bearer " + token)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"My Stream\",\"description\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.streamKey").exists())
                .andReturn();

        return created.getResponse().getContentAsString().split("\"streamKey\":\"")[1].split("\",\"")[0];
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return login.getResponse().getContentAsString().split("\"token\":\"")[1].split("\",\"")[0];
    }
}
