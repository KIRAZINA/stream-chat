package com.streamchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SettingsController.class)
@AutoConfigureMockMvc(addFilters = true)
@SuppressWarnings({"null", "NullAway"})
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StreamSettingsRepository streamSettingsRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void getSettings_success() throws Exception {
        StreamSettings settings = StreamSettings.builder()
                .id(1L)
                .slowModeEnabled(false)
                .slowModeSeconds(0)
                .followersOnlyMode(false)
                .subscribersOnlyMode(false)
                .emoteOnlyMode(false)
                .maxMessageLength(500)
                .profanityFilterEnabled(true)
                .linkProtectionEnabled(true)
                .build();

        when(streamSettingsRepository.findByStreamId(eq(1L)))
                .thenReturn(Optional.of(settings));

        mockMvc.perform(get("/api/streams/stream-abc/settings")
                        .with(user("broadcaster").roles("BROADCASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.maxMessageLength").value(500));

        verify(streamSettingsRepository).findByStreamId(eq(1L));
    }

    @Test
    void updateSettings_success() throws Exception {
        StreamSettings settings = StreamSettings.builder()
                .id(1L)
                .slowModeEnabled(false)
                .slowModeSeconds(0)
                .followersOnlyMode(false)
                .subscribersOnlyMode(false)
                .emoteOnlyMode(false)
                .maxMessageLength(500)
                .profanityFilterEnabled(true)
                .linkProtectionEnabled(true)
                .build();

        when(streamSettingsRepository.findByStreamId(eq(1L)))
                .thenReturn(Optional.of(settings));
        when(streamSettingsRepository.save(any(StreamSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/streams/stream-abc/settings")
                        .with(user("broadcaster").roles("BROADCASTER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "slowModeEnabled", true,
                                "slowModeSeconds", 3,
                                "maxMessageLength", 250
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slowModeEnabled").value(true))
                .andExpect(jsonPath("$.slowModeSeconds").value(3))
                .andExpect(jsonPath("$.maxMessageLength").value(250));

        verify(streamSettingsRepository).save(any(StreamSettings.class));
    }
}
