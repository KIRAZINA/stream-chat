package com.streamchat.config;

import com.streamchat.exception.ErrorResponse;
import com.streamchat.listener.RedisMessageSubscriber;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.service.RedisMessagePublisher;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracingIntegrationTest {

    @Test
    void redisPublisher_attachesTraceMetadata() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ChannelTopic chatTopic = new ChannelTopic("chat:messages");
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        RedisMessagePublisher publisher = new RedisMessagePublisher(redisTemplate, chatTopic, tracer);
        ChatMessageDTO message = ChatMessageDTO.builder().content("hi").build();
        publisher.publish("stream-1", message);

        assertEquals("trace-123", message.getTraceId());
        assertEquals("span-456", message.getSpanId());
        verify(redisTemplate).convertAndSend("chat:messages:stream-1", message);
    }

    @Test
    void redisPublisher_handlesNullTracer() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ChannelTopic chatTopic = new ChannelTopic("chat:messages");
        RedisMessagePublisher publisher = new RedisMessagePublisher(redisTemplate, chatTopic, null);
        ChatMessageDTO message = ChatMessageDTO.builder().content("hi").build();
        publisher.publish("stream-1", message);
        verify(redisTemplate).convertAndSend("chat:messages:stream-1", message);
    }

    @Test
    void redisSubscriber_restoresTraceScope() throws Exception {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        RedisMessageSubscriber subscriber = new RedisMessageSubscriber(messagingTemplate, objectMapper, null);

        ChatMessageDTO msg = ChatMessageDTO.builder().content("hi").traceId("t-1").spanId("s-1").build();
        String json = objectMapper.writeValueAsString(msg);
        org.springframework.data.redis.connection.DefaultMessage redisMsg =
                new org.springframework.data.redis.connection.DefaultMessage(
                        "chat:messages:stream-1".getBytes(), json.getBytes());

        subscriber.onMessage(redisMsg, null);
        // no exception; broadcast invoked
        verify(messagingTemplate).convertAndSend(eq("/topic/stream/stream-1"), Mockito.any(ChatMessageDTO.class));
    }

    @Test
    void errorResponse_traceIdFieldPresent() {
        ErrorResponse er = ErrorResponse.of(400, "Bad Request", "msg", "/p", "k", "trace-xyz");
        assertEquals("trace-xyz", er.getTraceId());
        assertNotNull(er.getTimestamp());
    }
}
