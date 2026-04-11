package com.streamchat.scheduled;

import com.streamchat.model.entity.BannedUser;
import com.streamchat.model.entity.TimedOutUser;
import com.streamchat.repository.BannedUserRepository;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.TimedOutUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled tasks for cleanup operations.
 * Runs periodically to remove expired data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final BannedUserRepository bannedUserRepository;
    private final TimedOutUserRepository timedOutUserRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${app.chat.retention-days:90}")
    private int retentionDays;

    /**
     * Clean up expired timeouts.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void cleanupExpiredTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<TimedOutUser> expired = timedOutUserRepository.findExpiredTimeouts(now);

        if (!expired.isEmpty()) {
            timedOutUserRepository.deleteAll(expired);
            log.info("Cleaned up {} expired timeouts", expired.size());
        }
    }

    /**
     * Clean up expired bans.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanupExpiredBans() {
        LocalDateTime now = LocalDateTime.now();
        List<BannedUser> expired = bannedUserRepository.findExpiredBans(now);

        if (!expired.isEmpty()) {
            bannedUserRepository.deleteAll(expired);
            log.info("Cleaned up {} expired bans", expired.size());
        }
    }

    /**
     * Clean up old chat messages based on retention policy.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3:00 AM
    @Transactional
    public void cleanupOldMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long countBefore = chatMessageRepository.countMessagesOlderThan(cutoff);

        if (countBefore > 0) {
            int deleted = chatMessageRepository.deleteMessagesOlderThan(cutoff);
            log.info("Retention cleanup: deleted {} messages older than {} days", deleted, retentionDays);
        }
    }
}