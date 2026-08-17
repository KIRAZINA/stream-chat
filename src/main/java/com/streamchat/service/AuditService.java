package com.streamchat.service;

import com.streamchat.model.entity.AuditLog;
import com.streamchat.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
