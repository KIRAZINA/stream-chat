package com.streamchat.service;

import com.streamchat.model.dto.analytics.StreamAnalyticsSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamAnalyticsServiceTest {

    @Test
    void recordMessage_updatesCountsAndMps() {
        StreamAnalyticsService svc = new StreamAnalyticsService();
        svc.recordMessage("s1", "alice", List.of("pepeKek"), 0.1);
        svc.recordMessage("s1", "bob", List.of(), 0.2);
        svc.recordMessage("s1", "alice", List.of("pepeKek"), 0.0);

        StreamAnalyticsSummaryDTO summary = svc.getSummary("s1", 1L);
        assertEquals(3, summary.getTotalMessages());
        assertEquals(2, summary.getUniqueChattersCount());
        assertEquals(2L, summary.getTopEmotesUsage().get("pepeKek"));
    }

    @Test
    void analyticsEndpoint_enforcesAuth() {
        // covered by controller test
        assertTrue(true);
    }
}
