package com.streamchat.service;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.entity.*;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatService.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ModerationService moderationService;

    @Mock
    private RedisMessagePublisher redisMessagePublisher;

    @InjectMocks
    private ChatService chatService;

    private User testUser;
    private Stream testStream;
    private StreamSettings testSettings;

    @BeforeEach
    void setUp() {
        // Manually inject Redis dependencies since they use field injection with @Autowired(required=false)
        ReflectionTestUtils.setField(chatService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(chatService, "redisMessagePublisher", redisMessagePublisher);
        
        // Setup test user
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .color("#FF0000")
                .build();

        // Setup test stream
        testStream = Stream.builder()
                .id(1L)
                .streamKey("test-stream")
                .user(testUser)
                .isLive(true)
                .build();

        // Setup test settings
        testSettings = StreamSettings.builder()
                .id(1L)
                .stream(testStream)
                .maxMessageLength(500)
                .profanityFilterEnabled(false)
                .linkProtectionEnabled(false)
                .build();

        testStream.setSettings(testSettings);
    }

    @Test
    void sendMessage_Success() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello, world!";

        // Setup Redis mocks for this test
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        ChatMessage savedMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username(username)
                .content(content)
                .messageType(MessageType.CHAT)
                .build();

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(savedMessage);

        // Act
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.CHAT);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(username, result.getUsername());
        assertEquals(content, result.getContent());
        assertEquals(MessageType.CHAT, result.getMessageType());

        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(redisMessagePublisher).publish(eq(streamKey), any(ChatMessageDTO.class));
        verify(listOperations).leftPush(anyString(), any(ChatMessageDTO.class));
        verify(listOperations).trim(anyString(), eq(0L), eq(99L));
        verify(redisTemplate).expire(anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    void sendMessage_UserBanned_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello, world!";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("banned"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_UserTimedOut_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello, world!";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("timed out"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_RateLimitExceeded_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Spam message";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(false);

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_MessageTooLong_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "a".repeat(600); // Exceeds max length of 500

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("maximum length"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_EmptyContent_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "   "; // Empty after trim

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("cannot be empty"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_WithLinksWhenProtectionEnabled_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Check out https://example.com";

        testSettings.setLinkProtectionEnabled(true);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("Links are not allowed"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_WithProfanityWhenFilterEnabled_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "This contains badword";

        testSettings.setProfanityFilterEnabled(true);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);
        when(moderationService.containsProfanity(content))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("blocked words"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void deleteMessage_Success() {
        // Arrange
        Long messageId = 1L;
        String deletedByUsername = "moderator";

        User moderator = User.builder()
                .id(2L)
                .username(deletedByUsername)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Test message")
                .build();

        when(chatMessageRepository.findById(messageId))
                .thenReturn(Optional.of(message));
        when(userRepository.findByUsername(deletedByUsername))
                .thenReturn(Optional.of(moderator));
        when(moderationService.canModerate(anyLong(), anyLong()))
                .thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        chatService.deleteMessage(messageId, deletedByUsername);

        // Assert
        verify(chatMessageRepository).save(argThat(msg ->
                msg.getIsDeleted() &&
                        msg.getDeletedBy().getId().equals(2L)
        ));
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void deleteMessage_UnauthorizedUser_ThrowsException() {
        // Arrange
        Long messageId = 1L;
        String deletedByUsername = "regularuser";

        User regularUser = User.builder()
                .id(2L)
                .username(deletedByUsername)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Test message")
                .build();

        when(chatMessageRepository.findById(messageId))
                .thenReturn(Optional.of(message));
        when(userRepository.findByUsername(deletedByUsername))
                .thenReturn(Optional.of(regularUser));
        when(moderationService.canModerate(anyLong(), anyLong()))
                .thenReturn(false);

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.deleteMessage(messageId, deletedByUsername));

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_WithoutRedis_Success() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(chatService, "redisTemplate", null);
        ReflectionTestUtils.setField(chatService, "redisMessagePublisher", null);

        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello without Redis!";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        ChatMessage savedMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username(username)
                .content(content)
                .messageType(MessageType.CHAT)
                .build();

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(savedMessage);

        // Act
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.CHAT);

        // Assert
        assertNotNull(result);
        assertEquals(content, result.getContent());
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(redisMessagePublisher, never()).publish(anyString(), any());
    }

    @Test
    void sendMessage_RedisError_GracefulDegradation() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello with Redis error!";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForList().leftPush(anyString(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        ChatMessage savedMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username(username)
                .content(content)
                .messageType(MessageType.CHAT)
                .build();

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(savedMessage);

        // Act - Should not throw exception, should continue despite Redis error
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.CHAT);

        // Assert
        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_StreamNotFound_ThrowsException() {
        // Arrange
        String streamKey = "non-existent-stream";
        String username = "testuser";
        String content = "Hello";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_UserNotFound_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "non-existent-user";
        String content = "Hello";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_WithNullContent_ThrowsException() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = null;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("cannot be empty"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_WithDefaultMaxLength_WhenSettingsNull() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "a".repeat(501); // Exceeds default 500

        testStream.setSettings(null);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("maximum length"));
    }

    @Test
    void sendMessage_WithDifferentMessageTypes() {
        // Arrange
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "System message";

        when(redisTemplate.opsForList()).thenReturn(listOperations);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);

        ChatMessage savedMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username(username)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .build();

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(savedMessage);

        // Act
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.SYSTEM);

        // Assert
        assertNotNull(result);
        assertEquals(MessageType.SYSTEM, result.getMessageType());
    }

    @Test
    void getRecentMessages_FromCache_Success() {
        // Arrange
        String streamKey = "test-stream";
        ChatMessageDTO cachedMessage = ChatMessageDTO.builder()
                .id(1L)
                .content("Cached message")
                .build();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenReturn(java.util.List.of(cachedMessage));

        // Act
        var result = chatService.getRecentMessages(streamKey);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Cached message", result.get(0).getContent());
        verify(chatMessageRepository, never())
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getRecentMessages_FromDatabase_WhenCacheEmpty() {
        // Arrange
        String streamKey = "test-stream";
        ChatMessage dbMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("DB message")
                .messageType(MessageType.CHAT)
                .build();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenReturn(java.util.List.of());
        when(chatMessageRepository.findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong()))
                .thenReturn(java.util.List.of(dbMessage));

        // Act
        var result = chatService.getRecentMessages(streamKey);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DB message", result.get(0).getContent());
        verify(chatMessageRepository, times(1))
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getRecentMessages_WithoutRedis_FromDatabase() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(chatService, "redisTemplate", null);

        String streamKey = "test-stream";
        ChatMessage dbMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("DB message")
                .messageType(MessageType.CHAT)
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong()))
                .thenReturn(java.util.List.of(dbMessage));

        // Act
        var result = chatService.getRecentMessages(streamKey);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(chatMessageRepository, times(1))
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getRecentMessages_StreamNotFound_ThrowsException() {
        // Arrange
        String streamKey = "non-existent-stream";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.getRecentMessages(streamKey));
    }

    @Test
    void getRecentMessages_RedisError_FallbackToDatabase() {
        // Arrange
        String streamKey = "test-stream";
        ChatMessage dbMessage = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("DB message")
                .messageType(MessageType.CHAT)
                .build();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenThrow(new RuntimeException("Redis error"));
        when(chatMessageRepository.findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong()))
                .thenReturn(java.util.List.of(dbMessage));

        // Act - Should not throw, should fallback to DB
        var result = chatService.getRecentMessages(streamKey);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(chatMessageRepository, times(1))
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void deleteMessage_WithoutRedis_Success() {
        // Arrange - Remove Redis
        ReflectionTestUtils.setField(chatService, "redisTemplate", null);

        Long messageId = 1L;
        String deletedByUsername = "moderator";

        User moderator = User.builder()
                .id(2L)
                .username(deletedByUsername)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Test message")
                .build();

        when(chatMessageRepository.findById(messageId))
                .thenReturn(Optional.of(message));
        when(userRepository.findByUsername(deletedByUsername))
                .thenReturn(Optional.of(moderator));
        when(moderationService.canModerate(anyLong(), anyLong()))
                .thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        chatService.deleteMessage(messageId, deletedByUsername);

        // Assert
        verify(chatMessageRepository).save(argThat(msg ->
                msg.getIsDeleted() && msg.getDeletedBy().getId().equals(2L)
        ));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void deleteMessage_MessageNotFound_ThrowsException() {
        // Arrange
        Long messageId = 999L;
        String deletedByUsername = "moderator";

        when(chatMessageRepository.findById(messageId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.deleteMessage(messageId, deletedByUsername));

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void deleteMessage_ModeratorNotFound_ThrowsException() {
        // Arrange
        Long messageId = 1L;
        String deletedByUsername = "non-existent-moderator";

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Test message")
                .build();

        when(chatMessageRepository.findById(messageId))
                .thenReturn(Optional.of(message));
        when(userRepository.findByUsername(deletedByUsername))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                chatService.deleteMessage(messageId, deletedByUsername));

        verify(chatMessageRepository, never()).save(any());
    }
}
