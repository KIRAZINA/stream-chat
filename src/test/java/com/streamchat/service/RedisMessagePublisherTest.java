package com.streamchat.service;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisMessagePublisher.
 */
@ExtendWith(MockitoExtension.class)
class RedisMessagePublisherTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChannelTopic chatTopic;

    @InjectMocks
    private RedisMessagePublisher redisMessagePublisher;

    private ChatMessageDTO testMessage;

    @BeforeEach
    void setUp() {
        testMessage = ChatMessageDTO.builder()
                .id(1L)
                .streamId(1L)
                .userId(1L)
                .username("testuser")
                .content("Test message")
                .messageType(MessageType.CHAT)
                .build();

        when(chatTopic.getTopic()).thenReturn("chat");
    }

    @Test
    void publish_Success() {
        String streamKey = "test-stream";
        String expectedChannel = "chat:test-stream";
        redisMessagePublisher.publish(streamKey, testMessage);
        verify(redisTemplate).convertAndSend(eq(expectedChannel), eq(testMessage));
    }

    @Test
    void publish_DifferentStreamKeys() {
        String streamKey1 = "stream1";
        String streamKey2 = "stream2";
        redisMessagePublisher.publish(streamKey1, testMessage);
        redisMessagePublisher.publish(streamKey2, testMessage);
        verify(redisTemplate).convertAndSend(eq("chat:stream1"), eq(testMessage));
        verify(redisTemplate).convertAndSend(eq("chat:stream2"), eq(testMessage));
    }

    @Test
    void publish_WithDifferentMessages() {
        String streamKey = "test-stream";
        
        ChatMessageDTO message1 = ChatMessageDTO.builder()
                .id(1L)
                .content("Message 1")
                .build();

        ChatMessageDTO message2 = ChatMessageDTO.builder()
                .id(2L)
                .content("Message 2")
                .build();
        redisMessagePublisher.publish(streamKey, message1);
        redisMessagePublisher.publish(streamKey, message2);
        verify(redisTemplate).convertAndSend(eq("chat:test-stream"), eq(message1));
        verify(redisTemplate).convertAndSend(eq("chat:test-stream"), eq(message2));
    }

    @Test
    void publish_RedisException_DoesNotThrow() {
        String streamKey = "test-stream";

        doThrow(new RuntimeException("Redis connection failed"))
                .when(redisTemplate).convertAndSend(anyString(), any());

                assertDoesNotThrow(() -> 
                redisMessagePublisher.publish(streamKey, testMessage));

        verify(redisTemplate).convertAndSend(anyString(), any());
    }

    @Test
    void publish_NullMessage_DoesNotThrow() {
        String streamKey = "test-stream";
        ChatMessageDTO nullMessage = null;

                assertDoesNotThrow(() -> 
                redisMessagePublisher.publish(streamKey, nullMessage));

        verify(redisTemplate).convertAndSend(anyString(), isNull());
    }

    @Test
    void publish_EmptyStreamKey() {
        String streamKey = "";
        redisMessagePublisher.publish(streamKey, testMessage);
        verify(redisTemplate).convertAndSend(eq("chat:"), eq(testMessage));
    }

    @Test
    void publish_SpecialCharactersInStreamKey() {
        String streamKey = "stream-key-123";
        redisMessagePublisher.publish(streamKey, testMessage);
        verify(redisTemplate).convertAndSend(eq("chat:stream-key-123"), eq(testMessage));
    }

    @Test
    void publish_ChannelTopicFormat() {
        String streamKey = "test-stream";
        when(chatTopic.getTopic()).thenReturn("custom-topic");
        redisMessagePublisher.publish(streamKey, testMessage);
        verify(redisTemplate).convertAndSend(eq("custom-topic:test-stream"), eq(testMessage));
    }

    @Test
    void publish_MultiplePublishes_SameStream() {
        String streamKey = "test-stream";
        String expectedChannel = "chat:test-stream";

        ChatMessageDTO message1 = ChatMessageDTO.builder().id(1L).content("Msg1").build();
        ChatMessageDTO message2 = ChatMessageDTO.builder().id(2L).content("Msg2").build();
        ChatMessageDTO message3 = ChatMessageDTO.builder().id(3L).content("Msg3").build();
        redisMessagePublisher.publish(streamKey, message1);
        redisMessagePublisher.publish(streamKey, message2);
        redisMessagePublisher.publish(streamKey, message3);
        verify(redisTemplate, times(3)).convertAndSend(eq(expectedChannel), any(ChatMessageDTO.class));
    }

    @Test
    void publish_MessageWithAllFields() {
        String streamKey = "test-stream";
        
        ChatMessageDTO fullMessage = ChatMessageDTO.builder()
                .id(100L)
                .streamId(1L)
                .userId(2L)
                .username("fulluser")
                .content("Full message content")
                .messageType(MessageType.SYSTEM)
                .color("#FF0000")
                .build();
        redisMessagePublisher.publish(streamKey, fullMessage);
        verify(redisTemplate).convertAndSend(eq("chat:test-stream"), eq(fullMessage));
    }
}
