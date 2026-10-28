package com.streamchat.controller;

import com.streamchat.aggregator.MultiPlatformChatAggregator;
import com.streamchat.model.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AggregatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MultiPlatformChatAggregator aggregator;

    @Test
    @WithMockUser
    void webhookProcessesMessage() throws Exception {
        when(aggregator.ingestExternalMessage(any(), any()))
                .thenReturn(ChatMessageDTO.builder().content("hi").build());

        mockMvc.perform(post("/api/streams/s1/aggregator/webhook/twitch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void webhookUnknownPlatformReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/streams/s1/aggregator/webhook/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }
}
