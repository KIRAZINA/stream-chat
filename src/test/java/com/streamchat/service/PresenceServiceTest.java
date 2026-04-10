package com.streamchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService();
    }

    @Test
    void registerSubscription_IncrementsActiveViewerCount() {
        assertTrue(presenceService.registerSubscription("session-1", "user1", "stream-abc"));
        assertEquals(1, presenceService.getActiveViewers("stream-abc"));

        assertFalse(presenceService.registerSubscription("session-1", "user1", "stream-abc"));
        assertEquals(1, presenceService.getActiveViewers("stream-abc"));

        assertFalse(presenceService.registerSubscription("session-2", "user1", "stream-abc"));
        assertEquals(1, presenceService.getActiveViewers("stream-abc"));

        assertTrue(presenceService.registerSubscription("session-3", "user2", "stream-abc"));
        assertEquals(2, presenceService.getActiveViewers("stream-abc"));
    }

    @Test
    void removeSubscription_DecrementsCountAndRemovesUserWhenNoSessionsRemain() {
        presenceService.registerSubscription("session-1", "user1", "stream-abc");
        presenceService.registerSubscription("session-2", "user1", "stream-abc");
        presenceService.registerSubscription("session-3", "user2", "stream-abc");

        assertEquals(2, presenceService.getActiveViewers("stream-abc"));
        assertFalse(presenceService.removeSubscription("session-1"));
        assertEquals(2, presenceService.getActiveViewers("stream-abc"));

        assertTrue(presenceService.removeSubscription("session-2"));
        assertEquals(1, presenceService.getActiveViewers("stream-abc"));

        assertTrue(presenceService.removeSubscription("session-3"));
        assertEquals(0, presenceService.getActiveViewers("stream-abc"));
    }
}
