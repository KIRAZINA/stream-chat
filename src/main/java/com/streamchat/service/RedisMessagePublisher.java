package com.streamchat.service;

import com.streamchat.model.dto.ChatMessageDTO;
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
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(RedisTemplate.class)
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic chatTopic;

    /**
     * Publish a message to Redis channel for distribution to all server instances.
     *
     * @param streamKey the stream identifier
     * @param message the message to publish
     * @return true if the message was handed off to Redis successfully
     */
    public boolean publish(String streamKey, ChatMessageDTO message) {
        try {
            String channel = chatTopic.getTopic() + ":" + streamKey;
            redisTemplate.convertAndSend(channel, message);
            Long messageId = message != null ? message.getId() : null;
            log.debug("Published message to Redis: channel={}, messageId={}", channel, messageId);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message to Redis: {}", e.getMessage(), e);
            return false;
        }
    }
}
