package com.streamchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.repository.ModerationLogRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ModerationController.class)
@AutoConfigureMockMvc(addFilters = true)
@SuppressWarnings({"null", "NullAway"})
class ModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ModerationService moderationService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ModerationLogRepository moderationLogRepository;

    @MockBean
    private UserStreamRoleRepository userStreamRoleRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void deleteMessage_success() throws Exception {
        mockMvc.perform(delete("/api/streams/stream-abc/moderate/messages/123")
                        .with(user("mod").roles("MODERATOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(chatService).deleteMessage(eq(123L), eq("mod"));
    }

    @Test
    void timeoutUser_success() throws Exception {
        mockMvc.perform(post("/api/streams/stream-abc/moderate/timeout")
                        .with(user("mod").roles("MODERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "target",
                                "durationSeconds", 60,
                                "reason", "spam"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(moderationService).timeoutUser(eq(1L), eq(1L), eq(1L), eq(60), eq("spam"));
    }

    @Test
    void banUser_success() throws Exception {
        mockMvc.perform(post("/api/streams/stream-abc/moderate/ban")
                        .with(user("mod").roles("MODERATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "target",
                                "reason", "toxicity",
                                "permanent", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(moderationService).banUser(eq(1L), eq(1L), eq(1L), eq(true), isNull(), eq("toxicity"));
    }

    @Test
    void getModerationLogs_success() throws Exception {
        when(moderationLogRepository.findByStreamIdOrderByCreatedAtDesc(eq(1L)))
                .thenReturn(List.of(ModerationLog.builder().id(1L).build()));

        mockMvc.perform(get("/api/streams/stream-abc/moderate/logs")
                        .with(user("mod").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(moderationLogRepository).findByStreamIdOrderByCreatedAtDesc(eq(1L));
    }

    @Test
    void getModerators_success() throws Exception {
        when(userStreamRoleRepository.findModerators(eq(1L)))
                .thenReturn(List.of(UserStreamRole.builder().id(1L).build()));

        mockMvc.perform(get("/api/streams/stream-abc/moderate/moderators")
                        .with(user("mod").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(userStreamRoleRepository).findModerators(eq(1L));
    }
}
