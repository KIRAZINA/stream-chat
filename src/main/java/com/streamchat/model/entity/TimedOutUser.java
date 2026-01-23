package com.streamchat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a timed out user in a stream.
 * Timeout prevents user from sending messages for a specified duration.
 */
@Entity
@Table(name = "timed_out_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimedOutUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private Long streamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "timed_out_by", nullable = false)
    private Long timedOutById;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if timeout is still active.
     */
    public boolean isActive() {
        return expiresAt.isAfter(LocalDateTime.now());
    }
}