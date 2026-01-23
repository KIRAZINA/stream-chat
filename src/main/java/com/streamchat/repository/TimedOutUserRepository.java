package com.streamchat.repository;

import com.streamchat.model.entity.TimedOutUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TimedOutUser entity operations.
 */
@Repository
public interface TimedOutUserRepository extends JpaRepository<TimedOutUser, Long> {

    /**
     * Find active timeout for user in stream.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @param now current timestamp
     * @return optional timeout
     */
    @Query("SELECT t FROM TimedOutUser t WHERE t.streamId = :streamId " +
            "AND t.userId = :userId AND t.expiresAt > :now")
    Optional<TimedOutUser> findActiveTimeout(Long streamId, Long userId, LocalDateTime now);

    /**
     * Check if user is timed out.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @param now current timestamp
     * @return true if timed out
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimedOutUser t " +
            "WHERE t.streamId = :streamId AND t.userId = :userId AND t.expiresAt > :now")
    boolean existsByStreamIdAndUserIdAndActiveTimeout(Long streamId, Long userId, LocalDateTime now);

    /**
     * Find all timeouts for a stream.
     *
     * @param streamId the stream ID
     * @return list of timeouts
     */
    List<TimedOutUser> findByStreamId(Long streamId);

    /**
     * Find expired timeouts for cleanup.
     *
     * @param now current timestamp
     * @return list of expired timeouts
     */
    @Query("SELECT t FROM TimedOutUser t WHERE t.expiresAt < :now")
    List<TimedOutUser> findExpiredTimeouts(LocalDateTime now);
}