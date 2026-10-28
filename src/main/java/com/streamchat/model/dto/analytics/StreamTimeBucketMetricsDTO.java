package com.streamchat.model.dto.analytics;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamTimeBucketMetricsDTO {
    private LocalDateTime bucketStartTime;
    private long messageCount;
    private long chatterCount;
    private double averageSpamScore;
}
