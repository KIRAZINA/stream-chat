package com.streamchat.model.entity;

import com.streamchat.model.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a chat message in a stream.
 * Messages can be regular chat, system notifications, or user actions.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id", nullable = false)
    private Stream stream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 20)
    @Builder.Default
    private MessageType messageType = MessageType.CHAT;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}