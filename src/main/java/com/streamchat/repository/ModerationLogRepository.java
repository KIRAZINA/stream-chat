package com.streamchat.repository;

import com.streamchat.model.entity.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}