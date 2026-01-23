package com.streamchat.repository;

import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.enums.ModerationActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ModerationLog entity operations.
 */
@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

    /**
     * Find moderation logs for a stream.
     *
     * @param streamId the stream ID
     * @return list of logs
     */
    List<ModerationLog> findByStreamIdOrderByCreatedAtDesc(Long streamId);

    /**
     * Find logs for a specific user.
     *
     * @param targetUserId the target user ID
     * @return list of logs
     */
    List<ModerationLog> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    /**
     * Find logs by moderator.
     *
     * @param moderatorId the moderator ID
     * @return list of logs
     */
    List<ModerationLog> findByModeratorIdOrderByCreatedAtDesc(Long moderatorId);

    /**
     * Find logs by action type.
     *
     * @param streamId the stream ID
     * @param actionType the action type
     * @return list of logs
     */
    List<ModerationLog> findByStreamIdAndActionType(Long streamId, ModerationActionType actionType);

    /**
     * Find recent logs in a time window.
     *
     * @param streamId the stream ID
     * @param after timestamp after which to search
     * @return list of logs
     */
    @Query("SELECT m FROM ModerationLog m WHERE m.streamId = :streamId " +
            "AND m.createdAt > :after ORDER BY m.createdAt DESC")
    List<ModerationLog> findRecentLogs(Long streamId, LocalDateTime after);
}