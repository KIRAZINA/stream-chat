package com.streamchat.service;

import com.streamchat.model.entity.AuditLog;
import com.streamchat.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service for audit logging and compliance.
 * Tracks administrative actions for accountability and security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an administrative action.
     */
    @Transactional
    public void logAction(Long actorId, String actorUsername, Long streamId,
                         Long targetUserId, String targetUsername,
                         String actionType, Map<String, Object> details,
                         String ipAddress, String userAgent) {
        String detailsJson = details != null ? details.toString() : null;

        AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .actorUsername(actorUsername)
                .streamId(streamId)
                .targetUserId(targetUserId)
                .targetUsername(targetUsername)
                .actionType(actionType)
                .details(detailsJson)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Audit log: actor={} action={} target={}", actorUsername, actionType, targetUsername);
    }

    /**
     * Log a simple administrative action.
     */
    @Transactional
    public void logAction(Long actorId, String actorUsername, String actionType, String details) {
        logAction(actorId, actorUsername, null, null, null, actionType,
                Map.of("details", details), null, null);
    }

    /**
     * Get audit logs for a stream.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsForStream(Long streamId, Pageable pageable) {
        return auditLogRepository.findByStreamId(streamId, pageable);
    }

    /**
     * Get audit logs by actor.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByActor(Long actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable);
    }

    /**
     * Get audit logs by action type.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByActionType(String actionType, Pageable pageable) {
        return auditLogRepository.findByActionType(actionType, pageable);
    }

    /**
     * Clean up old audit logs.
     */
    @Transactional
    public void cleanupOldAuditLogs(LocalDateTime cutoffDate) {
        auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Cleaned up audit logs older than {}", cutoffDate);
    }
}
