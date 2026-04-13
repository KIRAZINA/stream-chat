package com.streamchat.repository;

import com.streamchat.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Find audit logs by actor ID.
     */
    Page<AuditLog> findByActorId(Long actorId, Pageable pageable);

    /**
     * Find audit logs by stream ID.
     */
    Page<AuditLog> findByStreamId(Long streamId, Pageable pageable);

    /**
     * Find audit logs by action type.
     */
    Page<AuditLog> findByActionType(String actionType, Pageable pageable);

    /**
     * Find audit logs by actor and action type.
     */
    Page<AuditLog> findByActorIdAndActionType(Long actorId, String actionType, Pageable pageable);

    /**
     * Find audit logs within a time range.
     */
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find recent audit logs for a specific stream.
     */
    List<AuditLog> findTop50ByStreamIdOrderByCreatedAtDesc(Long streamId);

    /**
     * Find recent audit logs for a specific user.
     */
    List<AuditLog> findTop50ByActorIdOrderByCreatedAtDesc(Long actorId);

    /**
     * Delete audit logs older than a given date.
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}
