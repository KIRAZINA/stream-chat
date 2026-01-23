package com.streamchat.repository;

import com.streamchat.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ChatMessage entity operations.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Find recent messages for a stream (not deleted).
     *
     * @param streamId the stream ID
     * @return list of recent messages
     */
    List<ChatMessage> findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(Long streamId);

    /**
     * Find messages by user in a stream.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return list of messages
     */
    List<ChatMessage> findByStreamIdAndUserId(Long streamId, Long userId);

    /**
     * Count messages by user in a time window.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @param after timestamp after which to count
     * @return message count
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.stream.id = :streamId " +
            "AND m.user.id = :userId AND m.createdAt > :after")
    long countByStreamIdAndUserIdAfter(@Param("streamId") Long streamId,
                                       @Param("userId") Long userId,
                                       @Param("after") LocalDateTime after);

    /**
     * Delete all messages by user in a stream.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     */
    void deleteByStreamIdAndUserId(Long streamId, Long userId);
}