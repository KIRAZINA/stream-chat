package com.streamchat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a streaming session.
 * Each stream has its own chat room and settings.
 */
@Entity
@Table(name = "streams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_key", nullable = false, unique = true, length = 100)
    private String streamKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_live")
    @Builder.Default
    private Boolean isLive = false;

    @Column(name = "viewer_count")
    @Builder.Default
    private Integer viewerCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "stream", cascade = CascadeType.ALL, orphanRemoval = true)
    private StreamSettings settings;
}