package com.streamchat.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting application metrics using Micrometer.
 * Tracks message rates, rejections, moderation actions, and active users.
 */
@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter messagesSentCounter;
    private final Counter messagesRejectedCounter;
    private final Counter moderationActionsCounter;
    private final Counter rateLimitExceededCounter;
    private final Counter bannedMessagesCounter;
    private final Counter slowModeRedisFallbackCounter;
    private final Counter broadcastLocalCounter;
    private final Counter broadcastOrphanedCounter;

    // Timer for message processing
    private final Timer messageProcessingTimer;

    // Gauges for active users and streams
    private final AtomicLong totalActiveUsers = new AtomicLong(0);

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Message throughput
        this.messagesSentCounter = Counter.builder("chat.messages.sent")
                .description("Total number of messages sent")
                .tag("type", "all")
                .register(meterRegistry);

        // Rejected messages
        this.messagesRejectedCounter = Counter.builder("chat.messages.rejected")
                .description("Total number of messages rejected due to moderation")
                .register(meterRegistry);

        // Moderation actions
        this.moderationActionsCounter = Counter.builder("chat.moderation.actions")
                .description("Total moderation actions performed")
                .tags("action", "all")
                .register(meterRegistry);

        // Rate limit exceeded
        this.rateLimitExceededCounter = Counter.builder("chat.ratelimit.exceeded")
                .description("Total rate limit exceeded events")
                .register(meterRegistry);

        // Banned words detected
        this.bannedMessagesCounter = Counter.builder("chat.messages.banned_words")
                .description("Total messages containing banned words")
                .register(meterRegistry);

        // Slow mode Redis fallback (fail-open)
        this.slowModeRedisFallbackCounter = Counter.builder("chat.slowmode.redis_fallback")
                .description("Total slow-mode checks that fell back to fail-open because Redis was unavailable")
                .register(meterRegistry);

        // Local-first broadcast
        this.broadcastLocalCounter = Counter.builder("chat.broadcast.local")
                .description("Total messages delivered directly via the local broker (no Redis round trip)")
                .register(meterRegistry);

        this.broadcastOrphanedCounter = Counter.builder("chat.broadcast.orphaned")
                .description("Total messages broadcast locally to a stream with zero subscribers")
                .register(meterRegistry);

        // Message processing time
        this.messageProcessingTimer = Timer.builder("chat.message.processing.time")
                .description("Time to process a chat message")
                .sla(Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(500))
                .register(meterRegistry);

        // Active users gauge
        Gauge.builder("chat.users.active", totalActiveUsers, AtomicLong::get)
                .description("Current number of active users across all streams")
                .register(meterRegistry);

        log.info("MetricsService initialized with Micrometer registry: {}",
                meterRegistry.getClass().getSimpleName());
    }

    /**
     * Record a message sent to a specific stream.
     */
    public void recordMessageSent(String streamKey, String messageType) {
        Counter.builder("chat.messages.sent.by.stream")
                .description("Messages sent per stream")
                .tag("stream", streamKey)
                .tag("type", messageType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record a rejected message (e.g., banned word, rate limit).
     */
    public void recordMessageRejected(String reason) {
        messagesRejectedCounter.increment();

        Counter.builder("chat.messages.rejected.by.reason")
                .description("Rejected messages by reason")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record a moderation action.
     */
    public void recordModerationAction(String actionType) {
        moderationActionsCounter.increment();

        Counter.builder("chat.moderation.actions.by.type")
                .description("Moderation actions by type")
                .tag("action", actionType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record a rate limit exceeded event.
     */
    public void recordRateLimitExceeded() {
        rateLimitExceededCounter.increment();
    }

    /**
     * Record a message containing banned words.
     */
    public void recordBannedWordDetected() {
        bannedMessagesCounter.increment();
    }

    /**
     * Record a slow-mode check that fell back to fail-open because Redis
     * was unavailable.
     */
    public void recordSlowModeRedisFallback() {
        slowModeRedisFallbackCounter.increment();
    }

    /**
     * Record a message delivered directly via the local broker.
     */
    public void recordBroadcastLocal() {
        broadcastLocalCounter.increment();
    }

    /**
     * Record a local broadcast that had zero subscribers.
     */
    public void recordBroadcastOrphaned() {
        broadcastOrphanedCounter.increment();
    }

    /**
     * Record message processing time.
     */
    public void recordMessageProcessing(long durationMs) {
        messageProcessingTimer.record(Duration.ofMillis(durationMs));
    }
}
