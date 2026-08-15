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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private MetricsService metricsService;

    @Mock
    private AutoModService autoModService;

    @Mock
    private ChatMessagePersister chatMessagePersister;

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
        lenient().when(userBadgeRepository.findBadgeTypesByUserIdAndStreamIdOrGlobalIn(anySet(), anySet())).thenReturn(List.of());
        lenient().when(userBadgeRepository.hasBadge(anyLong(), anyLong(), anyString())).thenReturn(false);
        lenient().when(userBadgeRepository.hasBadgeGrantedBefore(anyLong(), anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(false);
        lenient().when(emoteService.buildMessageFragments(anyLong(), anyString())).thenReturn(List.of());
        lenient().doNothing().when(metricsService).recordMessageProcessing(anyLong());
        lenient().doNothing().when(metricsService).recordMessageSent(anyString(), anyString());
        lenient().doNothing().when(metricsService).recordMessageRejected(anyString());
        lenient().doNothing().when(metricsService).recordRateLimitExceeded();
        lenient().doNothing().when(metricsService).recordBannedWordDetected();
        lenient().when(autoModService.analyzeMessage(any(), any(), any()))
                .thenReturn(AutoModService.ModerationResult.allowed());

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

        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenReturn(savedMessage);
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.CHAT);
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(username, result.getUsername());
        assertEquals(content, result.getContent());
        assertEquals(MessageType.CHAT, result.getMessageType());

        verify(chatMessagePersister).persist(any(ChatMessage.class));
        verify(listOperations).leftPush(anyString(), any(ChatMessageDTO.class));
        verify(listOperations).trim(anyString(), eq(0L), eq(99L));
        verify(redisTemplate).expire(anyString(), eq(1L), eq(TimeUnit.HOURS));
    }

    @Test
    void sendMessage_UserBanned_ThrowsException() {
        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello, world!";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(true);
        Exception exception = assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("banned"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_UserTimedOut_ThrowsException() {
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
        Exception exception = assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("timed out"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_RateLimitExceeded_ThrowsException() {
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
        assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_MessageTooLong_ThrowsException() {
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
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("maximum length"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_EmptyContent_ThrowsException() {
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
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("cannot be empty"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_WithLinksWhenProtectionEnabled_ThrowsException() {
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
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("Links are not allowed"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_WithProfanityWhenFilterEnabled_ThrowsException() {
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
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("blocked words"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void deleteMessage_Success() {
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
        chatService.deleteMessage(messageId, deletedByUsername);
        verify(chatMessageRepository).save(argThat(msg ->
                msg.getIsDeleted() &&
                        msg.getDeletedBy().getId().equals(2L)
        ));
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void deleteMessage_UnauthorizedUser_ThrowsException() {
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
        assertThrows(RuntimeException.class, () ->
                chatService.deleteMessage(messageId, deletedByUsername));

        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_WithoutRedis_Success() {
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

        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenReturn(savedMessage);
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.CHAT);
        assertNotNull(result);
        assertEquals(content, result.getContent());
        verify(chatMessagePersister).persist(any(ChatMessage.class));
    }

    @Test
    void sendMessage_RedisError_GracefulDegradation() {
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

        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenReturn(savedMessage);
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.CHAT);
        assertNotNull(result);
        verify(chatMessagePersister).persist(any(ChatMessage.class));
    }

    @Test
    void sendMessage_StreamNotFound_ThrowsException() {
        String streamKey = "non-existent-stream";
        String username = "testuser";
        String content = "Hello";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_UserNotFound_ThrowsException() {
        String streamKey = "test-stream";
        String username = "non-existent-user";
        String content = "Hello";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_WithNullContent_ThrowsException() {
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
        lenient().when(autoModService.analyzeMessage(any(), any(), any()))
                .thenReturn(AutoModService.ModerationResult.allowed());
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("cannot be empty"));
        verify(chatMessagePersister, never()).persist(any());
    }

    @Test
    void sendMessage_WithDefaultMaxLength_WhenSettingsNull() {
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
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("maximum length"));
    }

    @Test
    void sendMessage_WithDifferentMessageTypes() {
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

        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenReturn(savedMessage);
        ChatMessageDTO result = chatService.sendMessage(
                streamKey, username, content, MessageType.SYSTEM);
        assertNotNull(result);
        assertEquals(MessageType.SYSTEM, result.getMessageType());
    }

    @Test
    void sendMessage_SlowModeEnabled_SecondMessageTooSoon_ThrowsException() {
        ReflectionTestUtils.setField(chatService, "redisTemplate", null);
        ReflectionTestUtils.setField(chatService, "slowModeStorage", "memory");

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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        chatService.sendMessage(streamKey, username, content, MessageType.CHAT);

        RateLimitException exception = assertThrows(RateLimitException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("Slow mode"));
        verify(metricsService, never()).recordSlowModeRedisFallback();
    }

    @Test
    void sendMessage_SlowModeRedis_AtomicScriptBlocked_ThrowsException() {
        ReflectionTestUtils.setField(chatService, "slowModeStorage", "redis");

        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello in slow mode";

        testSettings.setSlowModeEnabled(true);
        testSettings.setSlowModeSeconds(10);

        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenReturn(5000L);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);

        RateLimitException exception = assertThrows(RateLimitException.class, () ->
                chatService.sendMessage(streamKey, username, content, MessageType.CHAT));

        assertTrue(exception.getMessage().contains("Slow mode"));
        verify(chatMessagePersister, never()).persist(any());
        verify(metricsService, never()).recordSlowModeRedisFallback();
    }

    @Test
    void sendMessage_SlowModeRedis_Allowed_SendsAtomicScriptWithKeyAndTtl() {
        ReflectionTestUtils.setField(chatService, "slowModeStorage", "redis");

        String streamKey = "test-stream";
        String username = "testuser";

        testSettings.setSlowModeEnabled(true);
        testSettings.setSlowModeSeconds(10);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenReturn(0L);

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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);

        assertNotNull(result);

        ArgumentCaptor<RedisCallback> callbackCaptor = ArgumentCaptor.forClass(RedisCallback.class);
        verify(redisTemplate).execute(callbackCaptor.capture());

        RedisConnection connection = mock(RedisConnection.class);
        when(connection.eval(any(byte[].class), eq(ReturnType.INTEGER), eq(1), any(byte[][].class)))
                .thenReturn(0L);

        Object returned = callbackCaptor.getValue().doInRedis(connection);
        assertEquals(0L, returned);

        ArgumentCaptor<byte[]> scriptCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[][]> keysAndArgsCaptor = ArgumentCaptor.forClass(byte[][].class);
        verify(connection).eval(scriptCaptor.capture(), eq(ReturnType.INTEGER), eq(1), keysAndArgsCaptor.capture());
        assertArrayEquals(ChatService.SLOW_MODE_LUA.getBytes(StandardCharsets.UTF_8), scriptCaptor.getValue());
        byte[][] keysAndArgs = keysAndArgsCaptor.getValue();
        assertEquals("slowmode:lastmessage:1:1", new String(keysAndArgs[0], StandardCharsets.UTF_8));
        assertEquals("10000", new String(keysAndArgs[2], StandardCharsets.UTF_8));
        assertEquals("15", new String(keysAndArgs[3], StandardCharsets.UTF_8));
        verify(metricsService, never()).recordSlowModeRedisFallback();
    }

    @Test
    void sendMessage_SlowModeRedisDown_AllowsMessage() {
        ReflectionTestUtils.setField(chatService, "slowModeStorage", "redis");

        String streamKey = "test-stream";
        String username = "testuser";
        String content = "Hello in slow mode";

        testSettings.setSlowModeEnabled(true);
        testSettings.setSlowModeSeconds(10);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.execute(any(RedisCallback.class)))
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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, content, MessageType.CHAT);

        assertNotNull(result);
        verify(metricsService).recordSlowModeRedisFallback();
        verify(chatMessagePersister).persist(any(ChatMessage.class));
    }

    @Test
    void sendMessage_DuplicateIdempotencyKey_ReturnsExistingWithoutReconsumingQuota() {
        String streamKey = "test-stream";
        String username = "testuser";

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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO first = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT, null, "key-1");
        assertNotNull(first);
        assertEquals(1L, first.getId());

        ChatMessage existing = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username(username)
                .content("Hello")
                .messageType(MessageType.CHAT)
                .idempotencyKey("key-1")
                .build();
        when(chatMessageRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(existing));

        ChatMessageDTO second = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT, null, "key-1");

        assertEquals(1L, second.getId());
        verify(chatMessagePersister, times(1)).persist(any(ChatMessage.class));
        verify(rateLimitService, times(1)).allowMessage(anyLong(), anyLong());
        verify(autoModService, times(1)).analyzeMessage(any(), any(), any());
    }

    @Test
    void sendMessage_RaceDuplicateIdempotencyKey_RecoversFromViolation() {
        String streamKey = "test-stream";
        String username = "testuser";

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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ChatMessage existing = ChatMessage.builder()
                .id(1L)
                .stream(testStream)
                .user(testUser)
                .username(username)
                .content("Hello")
                .messageType(MessageType.CHAT)
                .idempotencyKey("key-race")
                .build();
        when(chatMessageRepository.findByIdempotencyKey("key-race"))
                .thenReturn(Optional.empty(), Optional.of(existing));

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT, null, "key-race");

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void sendMessage_DuplicateKey_OtherStream_Rethrows() {
        String streamKey = "test-stream";
        String username = "testuser";

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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        Stream otherStream = Stream.builder()
                .id(2L)
                .streamKey("other-stream")
                .user(streamOwner)
                .isLive(true)
                .build();
        ChatMessage existing = ChatMessage.builder()
                .id(1L)
                .stream(otherStream)
                .user(testUser)
                .username(username)
                .content("Hello")
                .messageType(MessageType.CHAT)
                .idempotencyKey("key-x")
                .build();
        when(chatMessageRepository.findByIdempotencyKey("key-x"))
                .thenReturn(Optional.empty(), Optional.of(existing));

        assertThrows(DataIntegrityViolationException.class, () ->
                chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT, null, "key-x"));
    }

    @Test
    void sendMessage_DuplicateIdempotencyKey_OtherStream_FastPathThrows() {
        String streamKey = "test-stream";
        String username = "testuser";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));

        Stream otherStream = Stream.builder()
                .id(2L)
                .streamKey("other-stream")
                .user(streamOwner)
                .isLive(true)
                .build();
        ChatMessage existing = ChatMessage.builder()
                .id(1L)
                .stream(otherStream)
                .user(testUser)
                .username(username)
                .content("Hello")
                .messageType(MessageType.CHAT)
                .idempotencyKey("key-fast-x")
                .build();
        when(chatMessageRepository.findByIdempotencyKey("key-fast-x"))
                .thenReturn(Optional.of(existing));

        assertThrows(DataIntegrityViolationException.class, () ->
                chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT, null, "key-fast-x"));
        verify(rateLimitService, never()).allowMessage(anyLong(), anyLong());
        verify(autoModService, never()).analyzeMessage(any(), any(), any());
    }

    @Test
    void sendMessage_NullIdempotencyKey_TwoSendsBothSucceed() {
        String streamKey = "test-stream";
        String username = "testuser";

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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);
        chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);

        verify(chatMessagePersister, times(2)).persist(any(ChatMessage.class));
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
        verify(chatMessagePersister, never()).persist(any());
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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);

        assertNotNull(result);
        verify(chatMessagePersister).persist(any(ChatMessage.class));
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
        verify(chatMessagePersister, never()).persist(any());
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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, "Hello", MessageType.CHAT);

        assertNotNull(result);
        verify(chatMessagePersister).persist(any(ChatMessage.class));
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
        verify(chatMessagePersister, never()).persist(any());
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
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        ChatMessageDTO result = chatService.sendMessage(streamKey, username, ":smile: :wave:", MessageType.CHAT);

        assertNotNull(result);
        verify(chatMessagePersister).persist(any(ChatMessage.class));
    }

    @Test
    void getRecentMessages_FromCache_Success() {
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
        var result = chatService.getRecentMessages(streamKey);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Cached message", result.get(0).getContent());
        verify(chatMessageRepository, never())
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getRecentMessages_FromDatabase_WhenCacheEmpty() {
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
        var result = chatService.getRecentMessages(streamKey);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DB message", result.get(0).getContent());
        verify(chatMessageRepository, times(1))
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getRecentMessages_WithoutRedis_FromDatabase() {
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
        var result = chatService.getRecentMessages(streamKey);
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(chatMessageRepository, times(1))
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getRecentMessages_StreamNotFound_ThrowsException() {
        String streamKey = "non-existent-stream";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
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
        assertEquals("Message deleted", result.getMessages().get(0).getContent());
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
        when(chatMessageRepository.findAllById(any()))
                .thenReturn(List.of(parent));

        var result = chatService.getMessageHistory(streamKey, null, 20);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        assertEquals(120L, result.getMessages().get(0).getReplyToMessageId());
        assertEquals("parentUser", result.getMessages().get(0).getReplyToUsername());
        assertTrue(result.getMessages().get(0).getReplyToContentPreview().contains("Hello from parent"));
        verify(chatMessageRepository).findAllById(any());
        verify(chatMessageRepository, never()).findById(120L);
    }

    @Test
    void getMessageHistory_NoReplies_IssuesNoBatchQuery() {
        String streamKey = "test-stream";

        ChatMessage noReply = ChatMessage.builder()
                .id(121L)
                .stream(testStream)
                .user(testUser)
                .username("replyUser")
                .content("Plain message")
                .messageType(MessageType.CHAT)
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of(noReply));

        var result = chatService.getMessageHistory(streamKey, null, 20);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        verify(chatMessageRepository, never()).findAllById(any());
    }

    @Test
    void getMessageHistory_ReplyToDeletedTarget_ShowsMessageDeleted() {
        String streamKey = "test-stream";

        ChatMessage deletedParent = ChatMessage.builder()
                .id(120L)
                .stream(testStream)
                .user(testUser)
                .username("parentUser")
                .content("Removed content")
                .isDeleted(true)
                .messageType(MessageType.DELETED)
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
        when(chatMessageRepository.findAllById(any()))
                .thenReturn(List.of(deletedParent));

        var result = chatService.getMessageHistory(streamKey, null, 20);

        assertEquals(1, result.getMessages().size());
        assertEquals("parentUser", result.getMessages().get(0).getReplyToUsername());
        assertEquals("Message deleted", result.getMessages().get(0).getReplyToContentPreview());
    }

    @Test
    void getMessageHistory_OrphanedReplyToId_NoPreviewNoException() {
        String streamKey = "test-stream";

        ChatMessage reply = ChatMessage.builder()
                .id(121L)
                .stream(testStream)
                .user(testUser)
                .username("replyUser")
                .content("Reply text")
                .replyToMessageId(999L)
                .messageType(MessageType.CHAT)
                .build();

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of(reply));
        when(chatMessageRepository.findAllById(any()))
                .thenReturn(List.of());

        var result = chatService.getMessageHistory(streamKey, null, 20);

        assertEquals(1, result.getMessages().size());
        assertEquals(999L, result.getMessages().get(0).getReplyToMessageId());
        assertNull(result.getMessages().get(0).getReplyToUsername());
        assertNull(result.getMessages().get(0).getReplyToContentPreview());
        verify(chatMessageRepository).findAllById(argThat(ids -> {
            for (Long id : ids) {
                if (Long.valueOf(999L).equals(id)) {
                    return true;
                }
            }
            return false;
        }));
    }

    @Test
    void sendMessage_AndHistory_ProduceIdenticalReplyPreview() {
        ReflectionTestUtils.setField(chatService, "redisTemplate", null);
        String streamKey = "test-stream";
        String username = "testuser";

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
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(moderationService.isUserBanned(anyLong(), anyLong()))
                .thenReturn(false);
        when(moderationService.isUserTimedOut(anyLong(), anyLong()))
                .thenReturn(false);
        when(rateLimitService.allowMessage(anyLong(), anyLong()))
                .thenReturn(true);
        when(chatMessagePersister.persist(any(ChatMessage.class)))
                .thenReturn(reply);
        when(chatMessageRepository.findById(120L))
                .thenReturn(Optional.of(parent));

        ChatMessageDTO single = chatService.sendMessage(
                streamKey, username, "Reply text", MessageType.CHAT, 120L);

        when(chatMessageRepository.findByStreamIdAndIsDeletedFalseOrderByIdDesc(eq(testStream.getId()), any()))
                .thenReturn(List.of(reply));
        when(chatMessageRepository.findAllById(any()))
                .thenReturn(List.of(parent));

        ChatMessageDTO batch = chatService.getMessageHistory(streamKey, null, 20)
                .getMessages().get(0);

        assertThat(single).usingRecursiveComparison().isEqualTo(batch);
        assertEquals("parentUser", single.getReplyToUsername());
        assertEquals("Hello from parent message", single.getReplyToContentPreview());
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
        var result = chatService.getRecentMessages(streamKey);
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(chatMessageRepository, times(1))
                .findTop100ByStreamIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void deleteMessage_WithoutRedis_Success() {
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
        chatService.deleteMessage(messageId, deletedByUsername);
        verify(chatMessageRepository).save(argThat(msg ->
                msg.getIsDeleted() && msg.getDeletedBy().getId().equals(2L)
        ));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void deleteMessage_MessageNotFound_ThrowsException() {
        Long messageId = 999L;
        String deletedByUsername = "moderator";

        when(chatMessageRepository.findById(messageId))
                .thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () ->
                chatService.deleteMessage(messageId, deletedByUsername));

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void deleteMessage_ModeratorNotFound_ThrowsException() {
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
        assertThrows(RuntimeException.class, () ->
                chatService.deleteMessage(messageId, deletedByUsername));

        verify(chatMessageRepository, never()).save(any());
    }
}

