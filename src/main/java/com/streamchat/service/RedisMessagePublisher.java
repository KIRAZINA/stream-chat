package com.streamchat.service;

import com.streamchat.model.dto.ChatMessageDTO;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

/**
 * Publisher for broadcasting messages via Redis pub/sub.
 * Enables synchronization across multiple server instances.
 * Only active when Redis is configured.
 */
@Service
@Slf4j
@ConditionalOnBean(RedisTemplate.class)
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic chatTopic;
    private final Tracer tracer; // may be null in unit tests

    public RedisMessagePublisher(RedisTemplate<String, Object> redisTemplate,
                                 ChannelTopic chatTopic,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false) Tracer tracer) {
        this.redisTemplate = redisTemplate;
        this.chatTopic = chatTopic;
        this.tracer = tracer;
    }

    /**
     * Publish a message to Redis channel for distribution to all server instances.
     */
    public boolean publish(String streamKey, ChatMessageDTO message) {
        try {
            // Propagate trace context into the payload envelope
            if (message != null && tracer != null) {
                Span current = tracer.currentSpan();
                if (current != null) {
                    message.setTraceId(current.context().traceId());
                    message.setSpanId(current.context().spanId());
                }
            }
            String channel = chatTopic.getTopic() + ":" + streamKey;
            redisTemplate.convertAndSend(channel, message);
            Long messageId = message != null ? message.getId() : null;
            log.debug("Published message to Redis: channel={}, messageId={}, traceId={}",
                    channel, messageId, message != null ? message.getTraceId() : null);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message to Redis: {}", e.getMessage(), e);
            return false;
        }
    }
}
