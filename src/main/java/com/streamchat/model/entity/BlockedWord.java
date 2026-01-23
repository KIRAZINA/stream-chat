package com.streamchat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a blocked word or phrase.
 * Can be global or stream-specific.
 */
@Entity
@Table(name = "blocked_words")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id")
    private Stream stream;

    @Column(nullable = false, length = 100)
    private String word;

    @Column(name = "is_regex")
    @Builder.Default
    private Boolean isRegex = false;

    @Column(name = "is_global")
    @Builder.Default
    private Boolean isGlobal = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}