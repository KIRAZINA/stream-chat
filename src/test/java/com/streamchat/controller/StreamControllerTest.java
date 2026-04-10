package com.streamchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.dto.ChatHistoryResponse;
import com.streamchat.model.dto.StreamDTO;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.ChatService;
import com.streamchat.service.StreamService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StreamController.class)
@AutoConfigureMockMvc(addFilters = true)
@SuppressWarnings({"null", "NullAway"})
class StreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StreamService streamService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void getLiveStreams_success() throws Exception {
        when(streamService.getLiveStreams()).thenReturn(List.of(
                StreamDTO.builder().id(1L).streamKey("s1").isLive(true).build(),
                StreamDTO.builder().id(2L).streamKey("s2").isLive(true).build()
        ));

        mockMvc.perform(get("/api/streams")
                        .with(user("viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(streamService).getLiveStreams();
    }

    @Test
    void getStream_success() throws Exception {
        when(streamService.getStreamByKey(eq("stream-abc")))
                .thenReturn(StreamDTO.builder().id(10L).streamKey("stream-abc").build());

        mockMvc.perform(get("/api/streams/stream-abc")
                        .with(user("viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.streamKey").value("stream-abc"));

        verify(streamService).getStreamByKey(eq("stream-abc"));
    }

    @Test
    void getChatHistory_success() throws Exception {
        when(chatService.getMessageHistory(eq("stream-abc"), eq(42L), eq(20)))
                .thenReturn(ChatHistoryResponse.builder()
                        .messages(List.of(
                                com.streamchat.model.dto.ChatMessageDTO.builder().id(50L).content("newer").build(),
                                com.streamchat.model.dto.ChatMessageDTO.builder().id(49L).content("older").build()
                        ))
                        .hasMore(true)
                        .nextCursor(49L)
                        .build());

        mockMvc.perform(get("/api/streams/stream-abc/messages")
                        .param("before", "42")
                        .param("limit", "20")
                        .with(user("viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].id").value(50))
                .andExpect(jsonPath("$.messages[1].content").value("older"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(49));

        verify(chatService).getMessageHistory(eq("stream-abc"), eq(42L), eq(20));
    }

    @Test
    void createStream_success() throws Exception {
        when(streamService.createStream(eq("alice"), eq("Title"), eq("Desc")))
                .thenReturn(StreamDTO.builder().id(1L).streamKey("k1").title("Title").description("Desc").build());

        mockMvc.perform(post("/api/streams")
                        .with(user("alice"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Title",
                                "description", "Desc"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streamKey").value("k1"))
                .andExpect(jsonPath("$.title").value("Title"));

        verify(streamService).createStream(eq("alice"), eq("Title"), eq("Desc"));
    }

    @Test
    void startStream_success() throws Exception {
        mockMvc.perform(post("/api/streams/stream-abc/start")
                        .with(user("alice"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(streamService).startStream(eq("stream-abc"), eq("alice"));
    }

    @Test
    void stopStream_success() throws Exception {
        mockMvc.perform(post("/api/streams/stream-abc/stop")
                        .with(user("alice"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(streamService).stopStream(eq("stream-abc"), eq("alice"));
    }
}
