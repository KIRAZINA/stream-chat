package com.streamchat.service;

import com.streamchat.exception.RateLimitException;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.entity.*;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.*;
import com.streamchat.service.EmoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
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
    private EmoteService emoteService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

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

    @InjectMocks
    private ChatService chatService;

    private User testUser;
    private User streamOwner;
    private Stream testStream;
    private StreamSettings testSettings;

    @BeforeEach
    void setUp() {
        // Manually inject Redis dependencies since they use field injection with @Autowired(required=false)
        ReflectionTestUtils.setField(chatService, "redisTemplate", redisTemplate);

        lenient().when(streamAuthorizationService.canModerate(anyString(), anyString())).thenReturn(false);
        lenient().when(userStreamRoleRepository.findByUserIdAndStreamId(anyLong(), anyLong())).thenReturn(List.of());
        lenient().when(userBadgeRepository.findBadgeTypesByUserIdAndStreamIdOrGlobal(anyLong(), anyLong())).thenReturn(List.of());
        lenient().when(userBadgeRepository.hasBadge(anyLong(), anyLong(), anyString())).thenReturn(false);
        lenient().when(userBadgeRepository.hasBadgeGrantedBefore(anyLong(), anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(false);
        lenient().when(emoteService.buildMessageFragments(anyLong(), anyString())).thenReturn(List.of());

        streamOwner = User.builder()
                .id(99L)
                .username("streamer")
                .email("streamer@example.com")
                .color("#00FF00")
                .build();

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
                .user(streamOwner)
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
        when(streamAuthorizationService.canModerate(anyString(), anyString()))
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
        when(streamAuthorizationService.canModerate(anyString(), anyString()))
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
    void sendMessage_SlowModeEnabled_SecondMessageTooSoon_ThrowsException() {
        ReflectionTestUtils.setField(chatService, "redisTemplate", null);

        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello in slow mode";

        testSettings.setSlowModeEnabled(true);
        testSettings.setSlowModeSeconds(10);

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
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        chatService.sendMessage(streamKey, username, content, MessageType.CHAT);

        RateLimitException exception = assertThrows(RateLimitException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("Slow mode"));
    }

    @Test
    void sendMessage_SubscribersOnly_NonSubscriber_ThrowsException() {
        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setSubscribersOnlyMode(true);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);

        Exception exception = assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT));

        assertTrue(exception.getMessage().contains("subscribers-only"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_SubscribersOnly_Subscriber_AllowsMessage() {
        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setSubscribersOnlyMode(true);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(true);
        when(userBadgeRepository.hasBadge(anyLong(), anyLong(), eq(com.streamchat.model.enums.UserBadge.SUBSCRIBER.name())))
                .thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_FollowersOnly_RecentFollower_ThrowsException() {
        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setFollowersOnlyMode(true);
        testSettings.setFollowersOnlyDurationMinutes(30);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);

        Exception exception = assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT));

        assertTrue(exception.getMessage().contains("followers-only"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_FollowersOnly_EligibleFollower_AllowsMessage() {
        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setFollowersOnlyMode(true);
        testSettings.setFollowersOnlyDurationMinutes(30);

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
        when(userBadgeRepository.hasBadgeGrantedBefore(
                anyLong(),
                anyLong(),
                eq(com.streamchat.model.enums.UserBadge.FOLLOWER.name()),
                any(LocalDateTime.class)))
                .thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_EmoteOnly_PlainText_ThrowsException() {
        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setEmoteOnlyMode(true);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(emoteRepository.existsByStreamIdAndCode(anyLong(), anyString()))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, "plain text", MessageType.CHAT));

        assertTrue(exception.getMessage().contains("emote-only"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_EmoteOnly_ValidEmotes_AllowsMessage() {
        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setEmoteOnlyMode(true);

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
        when(emoteRepository.existsByStreamIdAndCode(testStream.getId(), "smile"))
                .thenReturn(true);
        when(emoteRepository.existsByStreamIdAndCode(testStream.getId(), "wave"))
                .thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, ":smile: :wave:", MessageType.CHAT);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
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
    void getMessageHistory_FirstPage_ReturnsMessagesAndNextCursor() {
        String streamKey = "test-stream";

        ChatMessage message3 = ChatMessage.builder()
                .id(103L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Newest")
                .messageType(MessageType.CHAT)
                .build();
        ChatMessage message2 = ChatMessage.builder()
                .id(102L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Middle")
                .messageType(MessageType.CHAT)
                .build();
        ChatMessage message1 = ChatMessage.builder()
                .id(101L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Oldest")
                .messageType(MessageType.CHAT)
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of(message3, message2, message1));

        var result = chatService.getMessageHistory(streamKey, null, 2);

        assertNotNull(result);
        assertEquals(2, result.getMessages().size());
        assertEquals(103L, result.getMessages().get(0).getId());
        assertEquals(102L, result.getMessages().get(1).getId());
        assertTrue(result.isHasMore());
        assertEquals(102L, result.getNextCursor());
    }

    @Test
    void getMessageHistory_WithBeforeCursor_ReturnsOlderMessages() {
        String streamKey = "test-stream";

        ChatMessage older2 = ChatMessage.builder()
                .id(98L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Older 2")
                .messageType(MessageType.CHAT)
                .build();
        ChatMessage older1 = ChatMessage.builder()
                .id(97L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Older 1")
                .messageType(MessageType.CHAT)
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(
                eq(testStream.getId()),
                eq(99L),
                any()))
                .thenReturn(List.of(older2, older1));

        var result = chatService.getMessageHistory(streamKey, 99L, 20);

        assertNotNull(result);
        assertEquals(2, result.getMessages().size());
        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
        assertEquals("Older 2", result.getMessages().get(0).getContent());
    }

    @Test
    void getMessageHistory_IncludeDeleted_ReturnsTombstoneEntries() {
        String streamKey = "test-stream";

        ChatMessage deleted = ChatMessage.builder()
                .id(110L)
                .stream(testStream)
                .user(testUser)
                .username("testuser")
                .content("Sensitive text")
                .messageType(MessageType.CHAT)
                .isDeleted(true)
                .deletedAt(LocalDateTime.now())
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of(deleted));

        var result = chatService.getMessageHistory(streamKey, null, 20, true);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        assertTrue(result.getMessages().get(0).getIsDeleted());
        assertEquals(MessageType.DELETED, result.getMessages().get(0).getMessageType());
        assertEquals("Сообщение удалено", result.getMessages().get(0).getContent());
    }

    @Test
    void getMessageHistory_IncludesReplyPreview() {
        String streamKey = "test-stream";

        ChatMessage parent = ChatMessage.builder()
                .id(120L)
                .stream(testStream)
                .user(testUser)
                .username("parentUser")
                .content("Hello from parent message")
                .messageType(MessageType.CHAT)
                .build();

        ChatMessage reply = ChatMessage.builder()
                .id(121L)
                .stream(testStream)
                .user(testUser)
                .username("replyUser")
                .content("Reply text")
                .replyToMessageId(120L)
                .messageType(MessageType.CHAT)
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of(reply));
        when(chatMessageRepository.findById(120L))
                .thenReturn(Optional.of(parent));

        var result = chatService.getMessageHistory(streamKey, null, 20);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        assertEquals(120L, result.getMessages().get(0).getReplyToMessageId());
        assertEquals("parentUser", result.getMessages().get(0).getReplyToUsername());
        assertTrue(result.getMessages().get(0).getReplyToContentPreview().contains("Hello from parent"));
    }

    @Test
    void getMessageHistory_LimitIsNormalizedToMaximum() {
        String streamKey = "test-stream";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of());

        var result = chatService.getMessageHistory(streamKey, null, 500);

        assertNotNull(result);
        verify(chatMessageRepository).findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), argThat(
                pageable -> pageable.getPageSize() == 101
        ));
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
        when(streamAuthorizationService.canModerate(anyString(), anyString()))
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

