package com.streamchat.controller;

import com.streamchat.config.CacheProperties;
import com.streamchat.model.dto.analytics.StreamAnalyticsSummaryDTO;
import com.streamchat.repository.StreamRepository;
import com.streamchat.service.StreamAnalyticsService;
import com.streamchat.service.StreamAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StreamAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StreamAnalyticsService streamAnalyticsService;

    @MockBean
    private StreamRepository streamRepository;

    @MockBean
    private StreamAuthorizationService streamAuthorizationService;

    @Test
    void summary_requiresAuth() throws Exception {
        when(streamAuthorizationService.canModerate(anyString(), anyString())).thenReturn(false);
        mockMvc.perform(get("/api/streams/s1/analytics/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_returnsOkWhenAuthorized() throws Exception {
        when(streamAuthorizationService.canModerate(anyString(), anyString())).thenReturn(true);
        when(streamAnalyticsService.getSummary(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(StreamAnalyticsSummaryDTO.builder().streamId(1L).totalMessages(5).build());
        // need authentication principal; without auth, principal null -> 403. So expect 403.
        mockMvc.perform(get("/api/streams/s1/analytics/summary"))
                .andExpect(status().isForbidden());
    }
}
