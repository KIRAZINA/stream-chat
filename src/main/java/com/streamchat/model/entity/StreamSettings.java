package com.streamchat.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing chat settings for a stream.
 * Controls chat behavior and restrictions.
 */
@Entity
@Table(name = "stream_settings")
@JsonIgnoreProperties({"stream"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id", nullable = false, unique = true)
    private Stream stream;

    @Column(name = "slow_mode_enabled")
    @Builder.Default
    private Boolean slowModeEnabled = false;

    @Column(name = "slow_mode_seconds")
    @Builder.Default
    private Integer slowModeSeconds = 0;

    @Column(name = "followers_only_mode")
    @Builder.Default
    private Boolean followersOnlyMode = false;

    @Column(name = "followers_only_duration_minutes")
    @Builder.Default
    private Integer followersOnlyDurationMinutes = 0;

    @Column(name = "subscribers_only_mode")
    @Builder.Default
    private Boolean subscribersOnlyMode = false;

    @Column(name = "emote_only_mode")
    @Builder.Default
    private Boolean emoteOnlyMode = false;

    @Column(name = "max_message_length")
    @Builder.Default
    private Integer maxMessageLength = 500;

    @Column(name = "profanity_filter_enabled")
    @Builder.Default
    private Boolean profanityFilterEnabled = true;

    @Column(name = "link_protection_enabled")
    @Builder.Default
    private Boolean linkProtectionEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}