package com.streamchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitService.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // Manually inject Redis dependency since it uses field injection with @Autowired(required=false)
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", redisTemplate);
    }

    @Test
    void allowMessage_WithRedis_Success() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(1L);
        boolean result = rateLimitService.allowMessage(streamId, userId);
        assertTrue(result);
        verify(redisTemplate).expire(eq("ratelimit:1:2"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void allowMessage_WithRedis_ExceedsLimit() {
        Long streamId = 1L;
        Long userId = 2L;
        int maxMessages = 20;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(21L); // Exceeds limit
        boolean result = rateLimitService.allowMessage(streamId, userId, maxMessages, 60);
        assertFalse(result);
    }

    @Test
    void allowMessage_WithRedis_AtLimit() {
        Long streamId = 1L;
        Long userId = 2L;
        int maxMessages = 20;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(20L); // Exactly at limit
        boolean result = rateLimitService.allowMessage(streamId, userId, maxMessages, 60);
        assertTrue(result); // Should allow at limit
    }

    @Test
    void allowMessage_WithRedis_FirstMessage() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(1L);
        boolean result = rateLimitService.allowMessage(streamId, userId);
        assertTrue(result);
        verify(redisTemplate).expire(eq("ratelimit:1:2"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void allowMessage_WithRedis_SecondMessage_NoExpire() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(2L); // Not first message
        boolean result = rateLimitService.allowMessage(streamId, userId);
        assertTrue(result);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void allowMessage_WithRedis_NullCount_FailOpen() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(null); // Redis error
        boolean result = rateLimitService.allowMessage(streamId, userId);
        assertTrue(result); // Fail open
    }

    @Test
    void allowMessage_WithRedis_Exception_FallbackToInMemory() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2"))
                .thenThrow(new RuntimeException("Redis connection failed"));
        boolean result = rateLimitService.allowMessage(streamId, userId);
        assertTrue(result); // First message should be allowed
    }

    @Test
    void allowMessage_WithoutRedis_InMemory_Success() {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        boolean result = rateLimitService.allowMessage(streamId, userId);
        assertTrue(result);
    }

    @Test
    void allowMessage_WithoutRedis_InMemory_ExceedsLimit() {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        int maxMessages = 5;
        int windowSeconds = 60;
        for (int i = 0; i < maxMessages; i++) {
            assertTrue(rateLimitService.allowMessage(streamId, userId, maxMessages, windowSeconds));
        }

        // Next message should be blocked
        boolean result = rateLimitService.allowMessage(streamId, userId, maxMessages, windowSeconds);
        assertFalse(result);
    }

    @Test
    void allowMessage_WithoutRedis_InMemory_WindowExpires() throws InterruptedException {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        int maxMessages = 3;
        int windowSeconds = 1; // 1 second window
        for (int i = 0; i < maxMessages; i++) {
            assertTrue(rateLimitService.allowMessage(streamId, userId, maxMessages, windowSeconds));
        }

        // Should be blocked
        assertFalse(rateLimitService.allowMessage(streamId, userId, maxMessages, windowSeconds));

        // Wait for window to expire
        Thread.sleep(1100);

        // Should be allowed again
        assertTrue(rateLimitService.allowMessage(streamId, userId, maxMessages, windowSeconds));
    }

    @Test
    void resetRateLimit_WithRedis_Success() {
        Long streamId = 1L;
        Long userId = 2L;
        rateLimitService.resetRateLimit(streamId, userId);
        verify(redisTemplate).delete("ratelimit:1:2");
    }

    @Test
    void resetRateLimit_WithRedis_Exception_StillRemovesFromMemory() {
        Long streamId = 1L;
        Long userId = 2L;

        doThrow(new RuntimeException("Redis error"))
                .when(redisTemplate).delete("ratelimit:1:2");
        rateLimitService.resetRateLimit(streamId, userId);
        verify(redisTemplate).delete("ratelimit:1:2");
    }

    @Test
    void resetRateLimit_WithoutRedis_Success() {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;

        // First, add some rate limit entries
        rateLimitService.allowMessage(streamId, userId);
        rateLimitService.resetRateLimit(streamId, userId);

                assertDoesNotThrow(() -> rateLimitService.resetRateLimit(streamId, userId));
    }

    @Test
    void getRemainingMessages_WithRedis_NoMessages() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ratelimit:1:2")).thenReturn(null);
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        assertEquals(20, remaining); // Default max messages
    }

    @Test
    void getRemainingMessages_WithRedis_SomeMessagesUsed() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ratelimit:1:2")).thenReturn("5"); // 5 messages used
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        assertEquals(15, remaining); // 20 - 5 = 15
    }

    @Test
    void getRemainingMessages_WithRedis_AllMessagesUsed() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ratelimit:1:2")).thenReturn("20"); // All messages used
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        assertEquals(0, remaining);
    }

    @Test
    void getRemainingMessages_WithRedis_Exception_FallbackToInMemory() {
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ratelimit:1:2"))
                .thenThrow(new RuntimeException("Redis error"));
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        assertEquals(20, remaining); // Default when no entry in memory
    }

    @Test
    void getRemainingMessages_WithoutRedis_NoMessages() {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        assertEquals(20, remaining); // Default max messages
    }

    @Test
    void getRemainingMessages_WithoutRedis_SomeMessagesUsed() {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;

        // Use some messages
        rateLimitService.allowMessage(streamId, userId);
        rateLimitService.allowMessage(streamId, userId);
        rateLimitService.allowMessage(streamId, userId);
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        assertEquals(17, remaining); // 20 - 3 = 17
    }

    @Test
    void getRemainingMessages_WithoutRedis_NoEntry_ReturnsDefault() {
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        int remaining = rateLimitService.getRemainingMessages(streamId, userId);
        // When no entry exists, it returns DEFAULT_MAX_MESSAGES
        assertEquals(20, remaining); // Default max messages when no entry
    }

    @Test
    void allowMessage_CustomParameters() {
        Long streamId = 1L;
        Long userId = 2L;
        int maxMessages = 10;
        int windowSeconds = 30;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:1:2")).thenReturn(1L); // First message
        boolean result = rateLimitService.allowMessage(streamId, userId, maxMessages, windowSeconds);
        assertTrue(result);
        verify(redisTemplate).expire(eq("ratelimit:1:2"), eq(30L), eq(TimeUnit.SECONDS));
    }
}
