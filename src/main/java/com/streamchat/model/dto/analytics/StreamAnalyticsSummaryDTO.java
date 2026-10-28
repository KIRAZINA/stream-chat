package com.streamchat.model.dto.analytics;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamAnalyticsSummaryDTO {
    private Long streamId;
    private long totalMessages;
    private double currentMps;
    private double peakMps;
    private long uniqueChattersCount;
    private Map<String, Long> topEmotesUsage;
    private long deletedMessagesCount;
    private long timeoutCount;
    private double averageToxicityScore;
}
