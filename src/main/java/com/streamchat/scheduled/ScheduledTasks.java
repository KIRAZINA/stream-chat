package com.streamchat.scheduled;

import com.streamchat.model.entity.BannedUser;
import com.streamchat.model.entity.TimedOutUser;
import com.streamchat.repository.BannedUserRepository;
import com.streamchat.repository.TimedOutUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}