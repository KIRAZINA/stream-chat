package com.streamchat.service;

import com.streamchat.model.dto.analytics.StreamAnalyticsSummaryDTO;
import com.streamchat.model.dto.analytics.StreamTimeBucketMetricsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class StreamAnalyticsService {

    private static class StreamStats {
        final AtomicLong totalMessages = new AtomicLong();
        final AtomicLong deletedMessages = new AtomicLong();
        final AtomicLong timeoutCount = new AtomicLong();
        final AtomicLong uniqueChatters = new AtomicLong();
        final java.util.concurrent.ConcurrentHashMap<String, Boolean> chatters = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentHashMap<String, AtomicLong> emoteUsage = new java.util.concurrent.ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Long> messageTimestamps = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<LocalDateTime> bucketTimestamps = new ConcurrentLinkedQueue<>();
        final java.util.concurrent.ConcurrentHashMap<LocalDateTime, AtomicLong> bucketCounts = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentHashMap<LocalDateTime, Set<String>> bucketChatters = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ConcurrentHashMap<LocalDateTime, java.util.concurrent.atomic.DoubleAdder> bucketSpam = new java.util.concurrent.ConcurrentHashMap<>();
        double peakMps = 0;
        double toxicitySum = 0;
    }

    private final ConcurrentHashMap<String, StreamStats> statsMap = new ConcurrentHashMap<>();

    private StreamStats get(String streamKey) {
        return statsMap.computeIfAbsent(streamKey, k -> new StreamStats());
    }

    @Async
    public void recordMessage(String streamKey, String username, java.util.List<String> emoteCodes, double toxicityScore) {
        try {
            StreamStats s = get(streamKey);
            long now = System.currentTimeMillis();
            s.totalMessages.incrementAndGet();
            if (username != null && s.chatters.putIfAbsent(username, Boolean.TRUE) == null) {
                s.uniqueChatters.incrementAndGet();
            }
            s.messageTimestamps.add(now);
            s.toxicitySum += toxicityScore;
            if (emoteCodes != null) {
                for (String code : emoteCodes) {
                    s.emoteUsage.computeIfAbsent(code, k -> new AtomicLong()).incrementAndGet();
                }
            }
            LocalDateTime bucket = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            s.bucketCounts.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
            s.bucketChatters.computeIfAbsent(bucket, k -> ConcurrentHashMap.newKeySet()).add(username);
            s.bucketSpam.computeIfAbsent(bucket, k -> new java.util.concurrent.atomic.DoubleAdder()).add(toxicityScore);
            updatePeak(s);
        } catch (Exception e) {
            log.debug("Analytics recordMessage failed: {}", e.getMessage());
        }
    }

    @Async
    public void recordDeleted(String streamKey) {
        try { get(streamKey).deletedMessages.incrementAndGet(); }
        catch (Exception e) { log.debug("analytics deleted failed: {}", e.getMessage()); }
    }

    @Async
    public void recordTimeout(String streamKey) {
        try { get(streamKey).timeoutCount.incrementAndGet(); }
        catch (Exception e) { log.debug("analytics timeout failed: {}", e.getMessage()); }
    }

    private void updatePeak(StreamStats s) {
        double mps = currentMps(s);
        if (mps > s.peakMps) s.peakMps = mps;
    }

    private double currentMps(StreamStats s) {
        long cutoff = System.currentTimeMillis() - 60_000;
        while (!s.messageTimestamps.isEmpty() && s.messageTimestamps.peek() < cutoff) {
            s.messageTimestamps.poll();
        }
        return s.messageTimestamps.size(); // messages per 60s window; spec calls MPS, keep as count
    }

    public StreamAnalyticsSummaryDTO getSummary(String streamKey, Long streamId) {
        StreamStats s = statsMap.get(streamKey);
        if (s == null) {
            return StreamAnalyticsSummaryDTO.builder().streamId(streamId).build();
        }
        Map<String, Long> topEmotes = new java.util.LinkedHashMap<>();
        s.emoteUsage.forEach((k, v) -> topEmotes.put(k, v.get()));
        long total = s.totalMessages.get();
        double avgTox = total > 0 ? s.toxicitySum / total : 0;
        return StreamAnalyticsSummaryDTO.builder()
                .streamId(streamId)
                .totalMessages(total)
                .currentMps(currentMps(s))
                .peakMps(s.peakMps)
                .uniqueChattersCount(s.uniqueChatters.get())
                .topEmotesUsage(topEmotes)
                .deletedMessagesCount(s.deletedMessages.get())
                .timeoutCount(s.timeoutCount.get())
                .averageToxicityScore(avgTox)
                .build();
    }

    public List<StreamTimeBucketMetricsDTO> getTimeSeries(String streamKey, String interval, LocalDateTime from, LocalDateTime to) {
        StreamStats s = statsMap.get(streamKey);
        List<StreamTimeBucketMetricsDTO> result = new ArrayList<>();
        if (s == null) return result;
        for (Map.Entry<LocalDateTime, AtomicLong> e : s.bucketCounts.entrySet()) {
            LocalDateTime bt = e.getKey();
            if (from != null && bt.isBefore(from)) continue;
            if (to != null && bt.isAfter(to)) continue;
            Set<String> chatters = s.bucketChatters.get(bt);
            java.util.concurrent.atomic.DoubleAdder spam = s.bucketSpam.get(bt);
            result.add(StreamTimeBucketMetricsDTO.builder()
                    .bucketStartTime(bt)
                    .messageCount(e.getValue().get())
                    .chatterCount(chatters != null ? chatters.size() : 0)
                    .averageSpamScore(spam != null ? spam.sum() / e.getValue().get() : 0)
                    .build());
        }
        result.sort((a, b) -> a.getBucketStartTime().compareTo(b.getBucketStartTime()));
        return result;
    }
}
