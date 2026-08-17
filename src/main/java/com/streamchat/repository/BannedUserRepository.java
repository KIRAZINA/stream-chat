package com.streamchat.repository;

import com.streamchat.model.entity.BannedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for BannedUser entity operations.
 */
@Repository
public interface BannedUserRepository extends JpaRepository<BannedUser, Long> {

    /**
     * Check if user is banned (active ban).
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return true if actively banned
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BannedUser b " +
            "WHERE b.streamId = :streamId AND b.userId = :userId " +
            "AND (b.isPermanent = true OR b.expiresAt > CURRENT_TIMESTAMP)")
    boolean existsByStreamIdAndUserIdAndIsActiveBan(Long streamId, Long userId);

    /**
     * Find the active ban record for a user in a stream.
     * Activeness is computed in Java ({@code isActive()}), so the query mirrors
     * the entity semantics: permanent bans are active, temporary ones only while
     * {@code expires_at} is in the future.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return the active ban, if any
     */
    @Query("SELECT b FROM BannedUser b WHERE b.streamId = :streamId AND b.userId = :userId " +
            "AND (b.isPermanent = true OR b.expiresAt > CURRENT_TIMESTAMP)")
    Optional<BannedUser> findActiveBanByStreamAndUser(Long streamId, Long userId);

    /**
     * Find expired bans for cleanup.
     *
     * @param now current timestamp
     * @return list of expired bans
     */
    @Query("SELECT b FROM BannedUser b WHERE b.isPermanent = false " +
            "AND b.expiresAt < :now")
    List<BannedUser> findExpiredBans(LocalDateTime now);
}