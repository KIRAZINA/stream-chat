package com.streamchat.repository;

import com.streamchat.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
     * Find a page of recent messages for a stream ordered newest first.
     */
    List<ChatMessage> findByStreamIdAndIsDeletedFalseOrderByIdDesc(Long streamId, Pageable pageable);

    /**
     * Find a page of recent messages for a stream ordered newest first, including deleted tombstones.
     */
    List<ChatMessage> findByStreamIdOrderByIdDesc(Long streamId, Pageable pageable);

    /**
     * Find a page of older messages for a stream before a specific message id.
     */
    List<ChatMessage> findByStreamIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(Long streamId,
                                                                                 Long beforeMessageId,
                                                                                 Pageable pageable);

    /**
     * Find a page of older messages for a stream before a specific message id, including deleted tombstones.
     */
    List<ChatMessage> findByStreamIdAndIdLessThanOrderByIdDesc(Long streamId,
                                                               Long beforeMessageId,
                                                               Pageable pageable);

    /**
     * Find messages by user in a stream.
     *
     * @param streamId the stream ID
     * @param userId the user ID
     * @return list of messages
     */
    List<ChatMessage> findByStreamIdAndUserId(Long streamId, Long userId);

    /**
     * Find message by idempotency key.
     */
    Optional<ChatMessage> findByIdempotencyKey(String idempotencyKey);

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

    /**
     * Delete messages older than a given timestamp.
     * Used for retention policy cleanup.
     *
     * @param before the cutoff timestamp
     * @return number of deleted messages
     */
    @Query("DELETE FROM ChatMessage m WHERE m.createdAt < :before")
    int deleteMessagesOlderThan(@Param("before") LocalDateTime before);

    /**
     * Count messages older than a given timestamp.
     *
     * @param before the cutoff timestamp
     * @return number of messages
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.createdAt < :before")
    long countMessagesOlderThan(@Param("before") LocalDateTime before);
}
