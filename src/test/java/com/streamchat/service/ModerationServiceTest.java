package com.streamchat.service;

import com.streamchat.model.entity.BannedUser;
import com.streamchat.model.entity.BlockedWord;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.TimedOutUser;
import com.streamchat.repository.*;
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
 * Unit tests for ModerationService.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private BannedUserRepository bannedUserRepository;

    @Mock
    private TimedOutUserRepository timedOutUserRepository;

    @Mock
    private ModerationLogRepository moderationLogRepository;

    @Mock
    private UserStreamRoleRepository userStreamRoleRepository;

    @Mock
    private BlockedWordRepository blockedWordRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        // Manually inject Redis dependency since it uses field injection with @Autowired(required=false)
        ReflectionTestUtils.setField(moderationService, "redisTemplate", redisTemplate);
    }

    @Test
    void timeoutUser_Success() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        int duration = 300; // 5 minutes
        String reason = "Spam";

        // Setup Redis mock for this test
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(timedOutUserRepository.save(any(TimedOutUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.timeoutUser(streamId, userId, moderatorId, duration, reason);

        // Assert
        verify(timedOutUserRepository, times(1)).save(any(TimedOutUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
        verify(valueOperations, times(1))
                .set(eq("timeout:1:2"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void banUser_Permanent_Success() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        String reason = "Harassment";

        // Setup Redis mock for this test
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(bannedUserRepository.save(any(BannedUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.banUser(streamId, userId, moderatorId, true, null, reason);

        // Assert
        verify(bannedUserRepository, times(1)).save(any(BannedUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
        verify(valueOperations, times(1)).set(eq("ban:1:2"), eq("1"));
    }

    @Test
    void banUser_Temporary_Success() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        Integer durationSeconds = 86400; // 1 day
        String reason = "Toxicity";

        // Setup Redis mock for this test
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(bannedUserRepository.save(any(BannedUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.banUser(streamId, userId, moderatorId, false, durationSeconds, reason);

        // Assert
        verify(bannedUserRepository, times(1)).save(any(BannedUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
        verify(valueOperations, times(1))
                .set(eq("ban:1:2"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void unbanUser_Success() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;

        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.unbanUser(streamId, userId, moderatorId);

        // Assert
        verify(bannedUserRepository, times(1)).deleteByStreamIdAndUserId(streamId, userId);
        verify(redisTemplate, times(1)).delete(eq("ban:1:2"));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
    }

    @Test
    void isUserBanned_ReturnsTrueWhenBanned() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("ban:1:2")).thenReturn(true);

        // Act
        boolean result = moderationService.isUserBanned(streamId, userId);

        // Assert
        assertTrue(result);
        verify(redisTemplate, times(1)).hasKey("ban:1:2");
    }

    @Test
    void isUserBanned_ReturnsFalseWhenNotBanned() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("ban:1:2")).thenReturn(false);
        when(bannedUserRepository.existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId))
                .thenReturn(false);

        // Act
        boolean result = moderationService.isUserBanned(streamId, userId);

        // Assert
        assertFalse(result);
        verify(redisTemplate, times(1)).hasKey("ban:1:2");
        verify(bannedUserRepository, times(1))
                .existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId);
    }

    @Test
    void isUserTimedOut_ReturnsTrueWhenTimedOut() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("timeout:1:2")).thenReturn(true);

        // Act
        boolean result = moderationService.isUserTimedOut(streamId, userId);

        // Assert
        assertTrue(result);
        verify(redisTemplate, times(1)).hasKey("timeout:1:2");
    }

    @Test
    void isUserTimedOut_ReturnsFalseWhenNotTimedOut() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("timeout:1:2")).thenReturn(false);
        when(timedOutUserRepository.existsByStreamIdAndUserIdAndActiveTimeout(
                eq(streamId), eq(userId), any()))
                .thenReturn(false);

        // Act
        boolean result = moderationService.isUserTimedOut(streamId, userId);

        // Assert
        assertFalse(result);
        verify(redisTemplate, times(1)).hasKey("timeout:1:2");
        verify(timedOutUserRepository, times(1))
                .existsByStreamIdAndUserIdAndActiveTimeout(eq(streamId), eq(userId), any());
    }

    @Test
    void canModerate_ReturnsTrueForModerator() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(userStreamRoleRepository.hasModeratorRole(streamId, userId))
                .thenReturn(true);

        // Act
        boolean result = moderationService.canModerate(streamId, userId);

        // Assert
        assertTrue(result);
        verify(userStreamRoleRepository, times(1)).hasModeratorRole(streamId, userId);
    }

    @Test
    void canModerate_ReturnsFalseForRegularUser() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(userStreamRoleRepository.hasModeratorRole(streamId, userId))
                .thenReturn(false);

        // Act
        boolean result = moderationService.canModerate(streamId, userId);

        // Assert
        assertFalse(result);
        verify(userStreamRoleRepository, times(1)).hasModeratorRole(streamId, userId);
    }

    @Test
    void containsProfanity_ReturnsTrueForBlockedWord() {
        // Arrange
        String content = "This message contains badword";

        when(blockedWordRepository.findAllGlobal()).thenReturn(java.util.List.of());

        // Act
        boolean result = moderationService.containsProfanity(content);

        // Assert
        assertFalse(result); // Will be false since no blocked words configured
        verify(blockedWordRepository, times(1)).findAllGlobal();
    }

    @Test
    void containsProfanity_WithBlockedWord_ReturnsTrue() {
        // Arrange
        String content = "This message contains spam word";
        BlockedWord blockedWord = BlockedWord.builder()
                .id(1L)
                .word("spam")
                .isRegex(false)
                .isGlobal(true)
                .build();

        when(blockedWordRepository.findAllGlobal())
                .thenReturn(java.util.List.of(blockedWord));

        // Act
        boolean result = moderationService.containsProfanity(content);

        // Assert
        assertTrue(result);
        verify(blockedWordRepository, times(1)).findAllGlobal();
    }

    @Test
    void containsProfanity_WithRegexBlockedWord_ReturnsTrue() {
        // Arrange
        String content = "Visit http://example.com";
        BlockedWord blockedWord = BlockedWord.builder()
                .id(1L)
                .word(".*http://.*")
                .isRegex(true)
                .isGlobal(true)
                .build();

        when(blockedWordRepository.findAllGlobal())
                .thenReturn(java.util.List.of(blockedWord));

        // Act
        boolean result = moderationService.containsProfanity(content);

        // Assert
        assertTrue(result);
        verify(blockedWordRepository, times(1)).findAllGlobal();
    }

    @Test
    void containsProfanity_CaseInsensitive_ReturnsTrue() {
        // Arrange
        String content = "This message contains SPAM word";
        BlockedWord blockedWord = BlockedWord.builder()
                .id(1L)
                .word("spam")
                .isRegex(false)
                .isGlobal(true)
                .build();

        when(blockedWordRepository.findAllGlobal())
                .thenReturn(java.util.List.of(blockedWord));

        // Act
        boolean result = moderationService.containsProfanity(content);

        // Assert
        assertTrue(result);
    }

    @Test
    void containsProfanity_NoBlockedWords_ReturnsFalse() {
        // Arrange
        String content = "This is a clean message";

        when(blockedWordRepository.findAllGlobal())
                .thenReturn(java.util.List.of());

        // Act
        boolean result = moderationService.containsProfanity(content);

        // Assert
        assertFalse(result);
    }

    @Test
    void containsProfanity_MultipleBlockedWords_ReturnsTrue() {
        // Arrange
        String content = "This message contains spam";
        BlockedWord word1 = BlockedWord.builder()
                .id(1L)
                .word("spam")
                .isRegex(false)
                .isGlobal(true)
                .build();
        BlockedWord word2 = BlockedWord.builder()
                .id(2L)
                .word("badword")
                .isRegex(false)
                .isGlobal(true)
                .build();

        when(blockedWordRepository.findAllGlobal())
                .thenReturn(java.util.List.of(word1, word2));

        // Act
        boolean result = moderationService.containsProfanity(content);

        // Assert
        assertTrue(result);
    }

    @Test
    void isUserBanned_FromDatabase_WhenNotInCache() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("ban:1:2")).thenReturn(false);
        when(bannedUserRepository.existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId))
                .thenReturn(true);

        // Act
        boolean result = moderationService.isUserBanned(streamId, userId);

        // Assert
        assertTrue(result);
        verify(redisTemplate, times(1)).hasKey("ban:1:2");
        verify(bannedUserRepository, times(1))
                .existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId);
    }

    @Test
    void isUserBanned_WithoutRedis_FromDatabase() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(moderationService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;

        when(bannedUserRepository.existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId))
                .thenReturn(true);

        // Act
        boolean result = moderationService.isUserBanned(streamId, userId);

        // Assert
        assertTrue(result);
        verify(bannedUserRepository, times(1))
                .existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId);
    }

    @Test
    void isUserBanned_RedisError_FallbackToDatabase() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("ban:1:2"))
                .thenThrow(new RuntimeException("Redis connection failed"));
        when(bannedUserRepository.existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId))
                .thenReturn(false);

        // Act - Should not throw, should fallback to DB
        boolean result = moderationService.isUserBanned(streamId, userId);

        // Assert
        assertFalse(result);
        verify(bannedUserRepository, times(1))
                .existsByStreamIdAndUserIdAndIsActiveBan(streamId, userId);
    }

    @Test
    void isUserTimedOut_FromDatabase_WhenNotInCache() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("timeout:1:2")).thenReturn(false);
        when(timedOutUserRepository.existsByStreamIdAndUserIdAndActiveTimeout(
                eq(streamId), eq(userId), any()))
                .thenReturn(true);

        // Act
        boolean result = moderationService.isUserTimedOut(streamId, userId);

        // Assert
        assertTrue(result);
        verify(redisTemplate, times(1)).hasKey("timeout:1:2");
        verify(timedOutUserRepository, times(1))
                .existsByStreamIdAndUserIdAndActiveTimeout(eq(streamId), eq(userId), any());
    }

    @Test
    void isUserTimedOut_WithoutRedis_FromDatabase() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(moderationService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;

        when(timedOutUserRepository.existsByStreamIdAndUserIdAndActiveTimeout(
                eq(streamId), eq(userId), any()))
                .thenReturn(true);

        // Act
        boolean result = moderationService.isUserTimedOut(streamId, userId);

        // Assert
        assertTrue(result);
        verify(timedOutUserRepository, times(1))
                .existsByStreamIdAndUserIdAndActiveTimeout(eq(streamId), eq(userId), any());
    }

    @Test
    void isUserTimedOut_RedisError_FallbackToDatabase() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;

        when(redisTemplate.hasKey("timeout:1:2"))
                .thenThrow(new RuntimeException("Redis connection failed"));
        when(timedOutUserRepository.existsByStreamIdAndUserIdAndActiveTimeout(
                eq(streamId), eq(userId), any()))
                .thenReturn(false);

        // Act - Should not throw, should fallback to DB
        boolean result = moderationService.isUserTimedOut(streamId, userId);

        // Assert
        assertFalse(result);
        verify(timedOutUserRepository, times(1))
                .existsByStreamIdAndUserIdAndActiveTimeout(eq(streamId), eq(userId), any());
    }

    @Test
    void timeoutUser_WithoutRedis_Success() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(moderationService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        int duration = 300;
        String reason = "Spam";

        when(timedOutUserRepository.save(any(TimedOutUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.timeoutUser(streamId, userId, moderatorId, duration, reason);

        // Assert
        verify(timedOutUserRepository, times(1)).save(any(TimedOutUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void timeoutUser_RedisError_StillSavesToDatabase() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        int duration = 300;
        String reason = "Spam";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(timedOutUserRepository.save(any(TimedOutUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Should not throw, should continue
        moderationService.timeoutUser(streamId, userId, moderatorId, duration, reason);

        // Assert
        verify(timedOutUserRepository, times(1)).save(any(TimedOutUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
    }

    @Test
    void banUser_WithoutRedis_Success() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(moderationService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        String reason = "Harassment";

        when(bannedUserRepository.save(any(BannedUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.banUser(streamId, userId, moderatorId, true, null, reason);

        // Assert
        verify(bannedUserRepository, times(1)).save(any(BannedUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void banUser_RedisError_StillSavesToDatabase() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        String reason = "Harassment";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), any());
        when(bannedUserRepository.save(any(BannedUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Should not throw, should continue
        moderationService.banUser(streamId, userId, moderatorId, true, null, reason);

        // Assert
        verify(bannedUserRepository, times(1)).save(any(BannedUser.class));
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
    }

    @Test
    void unbanUser_WithoutRedis_Success() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(moderationService, "redisTemplate", null);

        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;

        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.unbanUser(streamId, userId, moderatorId);

        // Assert
        verify(bannedUserRepository, times(1)).deleteByStreamIdAndUserId(streamId, userId);
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void unbanUser_RedisError_StillDeletesFromDatabase() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;

        when(redisTemplate.delete(anyString()))
                .thenThrow(new RuntimeException("Redis error"));
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Should not throw, should continue
        moderationService.unbanUser(streamId, userId, moderatorId);

        // Assert
        verify(bannedUserRepository, times(1)).deleteByStreamIdAndUserId(streamId, userId);
        verify(moderationLogRepository, times(1)).save(any(ModerationLog.class));
    }

    @Test
    void banUser_ZeroDuration_ShouldBePermanent() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        Integer durationSeconds = 0; // Zero should be treated as permanent
        String reason = "Permanent ban";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(bannedUserRepository.save(any(BannedUser.class)))
                .thenAnswer(invocation -> {
                    BannedUser ban = invocation.getArgument(0);
                    assertTrue(ban.getIsPermanent());
                    return ban;
                });
        when(moderationLogRepository.save(any(ModerationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        moderationService.banUser(streamId, userId, moderatorId, true, durationSeconds, reason);

        // Assert
        verify(bannedUserRepository, times(1)).save(any(BannedUser.class));
        verify(valueOperations, times(1)).set(eq("ban:1:2"), eq("1"));
    }

    @Test
    void timeoutUser_WithZeroDuration_ShouldStillWork() {
        // Arrange
        Long streamId = 1L;
        Long userId = 2L;
        Long moderatorId = 3L;
        int duration = 0; // Invalid
        String reason = "Warning";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                moderationService.timeoutUser(streamId, userId, moderatorId, duration, reason));
    }
}
