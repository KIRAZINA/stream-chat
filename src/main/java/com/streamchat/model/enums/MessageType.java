package com.streamchat.model.enums;

/**
 * Enum representing different types of chat messages.
 */
public enum MessageType {
    CHAT,           // Regular chat message
    SYSTEM,         // System notification
    JOIN,           // User joined chat
    LEAVE,          // User left chat
    ERROR,          // Error message
    MODERATION,     // Moderation action notification
    DELETED         // Deleted message tombstone
}