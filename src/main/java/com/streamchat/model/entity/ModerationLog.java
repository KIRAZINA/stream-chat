package com.streamchat.model.entity;

import com.streamchat.model.enums.ModerationActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a moderation action log entry.
 * Tracks all moderation actions for auditing purposes.
 */
@Entity
@Table(name = "moderation_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private Long streamId;

    @Column(name = "moderator_id", nullable = false)
    private Long moderatorId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ModerationActionType actionType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}