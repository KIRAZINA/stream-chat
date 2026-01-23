package com.streamchat.repository;

import com.streamchat.model.entity.BannedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * Find ban record for user in stream.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return optional banned user
     */
    Optional<BannedUser> findByStreamIdAndUserId(Long streamId, Long userId);

    /**
     * Find all bans for a stream.
     *
     * @param streamId the stream ID
     * @return list of bans
     */
    List<BannedUser> findByStreamId(Long streamId);

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
     * Delete ban record.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     */
    @Modifying
    @Query("DELETE FROM BannedUser b WHERE b.streamId = :streamId AND b.userId = :userId")
    void deleteByStreamIdAndUserId(Long streamId, Long userId);

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