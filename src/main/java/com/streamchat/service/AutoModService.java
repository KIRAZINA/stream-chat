package com.streamchat.service;

import com.streamchat.model.entity.ChatMessage;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.repository.ModerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutoMod pipeline for automatic content moderation.
 * Analyzes messages for suspicious content and applies moderation actions.
 */
@Service
@Slf4j
public class AutoModService {

    private final ModerationService moderationService;
    private final ModerationLogRepository moderationLogRepository;
    private final MetricsService metricsService;

    // Trust score tracking per user per stream
    private final Map<String, UserTrustScore> userTrustScores = new ConcurrentHashMap<>();

    // Spam detection patterns
    private static final Pattern CAPS_PATTERN = Pattern.compile("([A-Z]{10,})");
    private static final Pattern REPEATED_CHARS_PATTERN = Pattern.compile("(.)\\1{4,}");
    private static final Pattern SPAM_LINK_PATTERN = Pattern.compile("(bit\\.ly|tinyurl|t\\.co|goo\\.gl)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{10,}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");

    // Thresholds
    @Value("${app.automod.caps-threshold:0.7}")
    private double capsThreshold;

    @Value("${app.automod.spam-score-threshold:3}")
    private int spamScoreThreshold;

    @Value("${app.automod.trust-decay-rate:0.1}")
    private double trustDecayRate;

    @Value("${app.automod.shadow-ban-enabled:true}")
    private boolean shadowBanEnabled;

    public AutoModService(ModerationService moderationService,
                          ModerationLogRepository moderationLogRepository,
                          MetricsService metricsService) {
        this.moderationService = moderationService;
        this.moderationLogRepository = moderationLogRepository;
        this.metricsService = metricsService;
    }

    /**
     * Analyze a message and return moderation result.
     * Returns true if the message should be blocked.
     */
    public ModerationResult analyzeMessage(Stream stream, User user, String content) {
        String key = stream.getId() + ":" + user.getId();
        UserTrustScore trustScore = userTrustScores.computeIfAbsent(key,
                k -> new UserTrustScore());

        // Decay trust over time
        trustScore.decayTrust(trustDecayRate);

        int spamScore = calculateSpamScore(content, trustScore);

        // Check for shadow ban
        if (shadowBanEnabled && trustScore.isShadowBanned()) {
            log.debug("Shadow-banned user {} in stream {}: message hidden",
                    user.getUsername(), stream.getId());
            return ModerationResult.shadowBlocked();
        }

        // High spam score - block message
        if (spamScore >= spamScoreThreshold) {
            trustScore.reduceTrust(spamScore);

            // Auto-timeout if trust is very low
            if (trustScore.getTrustScore() < -10) {
                applyAutoModeration(stream, user, content, spamScore);
                return ModerationResult.blocked("AutoMod: excessive spam detected");
            }

            return ModerationResult.blocked("AutoMod: spam score " + spamScore);
        }

        // Medium spam score - flag but allow
        if (spamScore >= spamScoreThreshold / 2) {
            trustScore.reduceTrust(spamScore / 2);
            log.debug("Flagged message from {} in stream {}: score={}",
                    user.getUsername(), stream.getId(), spamScore);
            return ModerationResult.flagged(spamScore);
        }

        // Clean message - increase trust
        trustScore.increaseTrust();
        return ModerationResult.allowed();
    }

    /**
     * Calculate spam score for a message.
     */
    private int calculateSpamScore(String content, UserTrustScore trustScore) {
        int score = 0;

        // Check for excessive caps
        if (hasExcessiveCaps(content)) {
            score += 2;
        }

        // Check for repeated characters
        if (hasRepeatedChars(content)) {
            score += 1;
        }

        // Check for spam links
        if (hasSpamLinks(content)) {
            score += 3;
        }

        // Check for phone numbers (potential scam)
        if (hasPhoneNumbers(content)) {
            score += 2;
        }

        // Check for email addresses (potential phishing)
        if (hasEmails(content)) {
            score += 1;
        }

        // Check for excessive length
        if (content.length() > 300) {
            score += 1;
        }

        // Reduce score for trusted users
        if (trustScore.getTrustScore() > 5) {
            score = Math.max(0, score - 1);
        }

        return score;
    }

    private boolean hasExcessiveCaps(String content) {
        long capsCount = content.chars().filter(Character::isUpperCase).count();
        long letterCount = content.chars().filter(Character::isLetter).count();
        if (letterCount == 0) return false;
        return (double) capsCount / letterCount > capsThreshold;
    }

    private boolean hasRepeatedChars(String content) {
        return REPEATED_CHARS_PATTERN.matcher(content).find();
    }

    private boolean hasSpamLinks(String content) {
        return SPAM_LINK_PATTERN.matcher(content).find();
    }

    private boolean hasPhoneNumbers(String content) {
        return PHONE_PATTERN.matcher(content).find();
    }

    private boolean hasEmails(String content) {
        return EMAIL_PATTERN.matcher(content).find();
    }

    /**
     * Apply automatic moderation action.
     */
    private void applyAutoModeration(Stream stream, User user, String content, int spamScore) {
        try {
            // Auto-timeout for 10 minutes
            moderationService.timeoutUser(
                    stream.getId(),
                    user.getId(),
                    null, // No moderator (system action)
                    600,
                    "AutoMod: automatic timeout for spam (score: " + spamScore + ")"
            );

            // Log the action
            ModerationLog modLog = ModerationLog.builder()
                    .streamId(stream.getId())
                    .moderatorId(null)
                    .targetUserId(user.getId())
                    .actionType(ModerationActionType.TIMEOUT)
                    .reason("AutoMod: automatic timeout for spam (score: " + spamScore + ")")
                    .durationSeconds(600)
                    .build();
            moderationLogRepository.save(modLog);

            metricsService.recordModerationAction("automod_timeout");

            log.info("AutoMod: auto-timeout applied to user {} in stream {} (score: {})",
                    user.getUsername(), stream.getId(), spamScore);
        } catch (Exception e) {
            log.error("AutoMod: failed to apply moderation: {}", e.getMessage());
        }
    }

    /**
     * Enable shadow ban for a user.
     */
    public void enableShadowBan(Long streamId, Long userId) {
        String key = streamId + ":" + userId;
        userTrustScores.computeIfAbsent(key, k -> new UserTrustScore())
                .setShadowBanned(true);
        log.info("Shadow ban enabled for user {} in stream {}", userId, streamId);
        metricsService.recordModerationAction("shadow_ban");
    }

    /**
     * Disable shadow ban for a user.
     */
    public void disableShadowBan(Long streamId, Long userId) {
        String key = streamId + ":" + userId;
        UserTrustScore score = userTrustScores.get(key);
        if (score != null) {
            score.setShadowBanned(false);
        }
        log.info("Shadow ban disabled for user {} in stream {}", userId, streamId);
    }

    /**
     * Check if a user is shadow banned.
     */
    public boolean isShadowBanned(Long streamId, Long userId) {
        String key = streamId + ":" + userId;
        UserTrustScore score = userTrustScores.get(key);
        return score != null && score.isShadowBanned();
    }

    /**
     * Get trust score for a user.
     */
    public double getTrustScore(Long streamId, Long userId) {
        String key = streamId + ":" + userId;
        UserTrustScore score = userTrustScores.get(key);
        return score != null ? score.getTrustScore() : 0.0;
    }

    /**
     * Result of AutoMod analysis.
     */
    public static class ModerationResult {
        private final Action action;
        private final String reason;
        private final Integer spamScore;

        public enum Action {
            ALLOWED, FLAGGED, BLOCKED, SHADOW_BLOCKED
        }

        private ModerationResult(Action action, String reason, Integer spamScore) {
            this.action = action;
            this.reason = reason;
            this.spamScore = spamScore;
        }

        public static ModerationResult allowed() {
            return new ModerationResult(Action.ALLOWED, null, null);
        }

        public static ModerationResult flagged(int score) {
            return new ModerationResult(Action.FLAGGED, "Flagged as suspicious", score);
        }

        public static ModerationResult blocked(String reason) {
            return new ModerationResult(Action.BLOCKED, reason, null);
        }

        public static ModerationResult shadowBlocked() {
            return new ModerationResult(Action.SHADOW_BLOCKED, "Shadow banned", null);
        }

        public boolean isBlocked() {
            return action == Action.BLOCKED || action == Action.SHADOW_BLOCKED;
        }

        public Action getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public Integer getSpamScore() {
            return spamScore;
        }
    }

    /**
     * Trust score for a user in a stream.
     */
    private static class UserTrustScore {
        private double trustScore = 0.0;
        private boolean shadowBanned = false;
        private long lastActivityTime = System.currentTimeMillis();

        public double getTrustScore() {
            return trustScore;
        }

        public void increaseTrust() {
            trustScore = Math.min(20, trustScore + 0.5);
        }

        public void reduceTrust(int penalty) {
            trustScore = Math.max(-20, trustScore - penalty);
        }

        public void decayTrust(double rate) {
            long now = System.currentTimeMillis();
            long timeSinceLastActivity = now - lastActivityTime;
            lastActivityTime = now;

            // Decay trust towards 0 over time
            if (Math.abs(timeSinceLastActivity) > 60000) { // After 1 minute of inactivity
                if (trustScore > 0) {
                    trustScore = Math.max(0, trustScore - rate);
                }
            }
        }

        public boolean isShadowBanned() {
            return shadowBanned;
        }

        public void setShadowBanned(boolean shadowBanned) {
            this.shadowBanned = shadowBanned;
        }
    }
}
