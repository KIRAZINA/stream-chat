package com.streamchat.repository;

import com.streamchat.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit logs.
 * Supports compliance and security auditing.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find recent audit logs for a specific stream.
     */
    List<AuditLog> findTop50ByStreamIdOrderByCreatedAtDesc(Long streamId);

    /**
     * Delete audit logs older than a given date.
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}
