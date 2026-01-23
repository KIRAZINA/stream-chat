package com.streamchat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a banned user in a stream.
 * Can be permanent or temporary ban.
 */
@Entity
@Table(name = "banned_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private Long streamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "banned_by", nullable = false)
    private Long bannedById;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "is_permanent")
    @Builder.Default
    private Boolean isPermanent = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if ban is still active.
     */
    public boolean isActive() {
        if (isPermanent) {
            return true;
        }
        return expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}