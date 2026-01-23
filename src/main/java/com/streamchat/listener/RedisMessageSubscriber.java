package com.streamchat.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Subscriber for receiving messages from Redis pub/sub.
 * Broadcasts received messages to WebSocket clients on this server instance.
 * Only active when Redis is configured.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(RedisTemplate.class)
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Handle incoming messages from Redis.
     * Deserializes and broadcasts to WebSocket subscribers.
     *
     * @param message the Redis message
     * @param pattern the subscription pattern
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Deserialize message body
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessageDTO chatMessage = objectMapper.readValue(json, ChatMessageDTO.class);

            // Extract stream key from channel name
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String streamKey = channel.substring(channel.lastIndexOf(":") + 1);

            log.debug("Received message from Redis: channel={}, messageId={}",
                    channel, chatMessage.getId());

            // Broadcast to WebSocket clients
            messagingTemplate.convertAndSend(
                    "/topic/stream/" + streamKey,
                    chatMessage
            );

        } catch (Exception e) {
            log.error("Error processing Redis message: {}", e.getMessage(), e);
        }
    }
}