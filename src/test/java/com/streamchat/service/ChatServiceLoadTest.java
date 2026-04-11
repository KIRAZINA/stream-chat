package com.streamchat.service;

import com.streamchat.exception.RateLimitException;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.EmoteRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserBadgeRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Load-oriented tests for rate limits and moderation paths.
 * Verifies system behavior under concurrent message sending.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceLoadTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private StreamRepository streamRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private ModerationService moderationService;
    @Mock
    private StreamAuthorizationService streamAuthorizationService;
    @Mock
    private UserStreamRoleRepository userStreamRoleRepository;
    @Mock
    private UserBadgeRepository userBadgeRepository;
    @Mock
    private EmoteRepository emoteRepository;
    @Mock
    private EmoteService emoteService;
    @Mock
    private MetricsService metricsService;
    @Mock
    private AutoModService autoModService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatMessageRepository, streamRepository, userRepository,
                rateLimitService, moderationService, streamAuthorizationService,
                userStreamRoleRepository, userBadgeRepository, emoteRepository,
                emoteService, metricsService, autoModService);
    }

    @Test
    void concurrentMessageSending_handlesRateLimits() throws Exception {
        // Setup
        Stream stream = createTestStream();
        User user = createTestUser();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(moderationService.isUserBanned(anyLong(), anyLong())).thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong())).thenReturn(false);
        when(userBadgeRepository.hasBadge(anyLong(), anyLong(), anyString())).thenReturn(false);
        when(emoteRepository.existsByStreamIdAndCode(anyLong(), anyString())).thenReturn(false);
        when(emoteService.buildMessageFragments(anyLong(), anyString())).thenReturn(java.util.List.of());
        when(autoModService.analyzeMessage(any(), any(), anyString()))
                .thenReturn(AutoModService.ModerationResult.allowed());

        // Simulate rate limit after 5 messages
        AtomicInteger callCount = new AtomicInteger(0);
        when(rateLimitService.allowMessage(anyLong(), anyLong(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int count = callCount.incrementAndGet();
            return count <= 5; // Allow first 5, then block
        });

        when(chatMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Run concurrent requests
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    chatService.sendMessage("stream-1", "user1", "Hello", MessageType.CHAT);
                    successCount.incrementAndGet();
                } catch (RateLimitException e) {
                    rateLimitCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All requests should complete within timeout");
        executor.shutdown();

        // Verify results
        assertEquals(0, errorCount.get(), "No unexpected errors should occur");
        assertTrue(successCount.get() > 0, "Some messages should succeed");
        assertTrue(rateLimitCount.get() > 0, "Some requests should be rate limited");
        assertEquals(threadCount, successCount.get() + rateLimitCount.get(),
                "All requests should either succeed or be rate limited");
    }

    @Test
    void highThroughput_maintainsPerformance() throws Exception {
        // Setup
        Stream stream = createTestStream();
        User user = createTestUser();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(moderationService.isUserBanned(anyLong(), anyLong())).thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong())).thenReturn(false);
        when(userBadgeRepository.hasBadge(anyLong(), anyLong(), anyString())).thenReturn(false);
        when(emoteRepository.existsByStreamIdAndCode(anyLong(), anyString())).thenReturn(false);
        when(emoteService.buildMessageFragments(anyLong(), anyString())).thenReturn(java.util.List.of());
        when(autoModService.analyzeMessage(any(), any(), anyString()))
                .thenReturn(AutoModService.ModerationResult.allowed());
        when(rateLimitService.allowMessage(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(true);
        when(chatMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int messageCount = 100;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < messageCount; i++) {
            chatService.sendMessage("stream-1", "user1", "Message " + i, MessageType.CHAT);
        }

        long duration = System.currentTimeMillis() - startTime;

        // 100 messages should complete in under 2 seconds
        assertTrue(duration < 2000, "100 messages should complete in under 2 seconds, took: " + duration + "ms");
    }

    private Stream createTestStream() {
        Stream stream = new Stream();
        stream.setId(1L);
        stream.setStreamKey("stream-1");
        stream.setUser(createTestUser());
        stream.setSettings(new StreamSettings());
        return stream;
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("user1");
        user.setColor("#bf94ff");
        return user;
    }
}
