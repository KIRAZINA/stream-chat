package com.streamchat.model.enums;

/**
 * Enum representing types of moderation actions.
 */
public enum ModerationActionType {
    TIMEOUT,        // Temporarily mute user
    BAN,            // Permanently ban user
    UNBAN,          // Remove ban
    DELETE_MESSAGE, // Delete a specific message
    CLEAR_CHAT      // Clear all chat messages
}