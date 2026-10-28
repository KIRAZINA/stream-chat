package com.streamchat.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.model.dto.ChatMessageDTO;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
@Slf4j
@ConditionalOnBean(RedisTemplate.class)
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate,
                                 ObjectMapper objectMapper,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false) Tracer tracer) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * Handle incoming messages from Redis.
     * Deserializes and broadcasts to WebSocket subscribers.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String previousTraceId = MDC.get("traceId");
        String previousSpanId = MDC.get("spanId");
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessageDTO chatMessage = objectMapper.readValue(json, ChatMessageDTO.class);

            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String streamKey = channel.substring(channel.lastIndexOf(":") + 1);

            // Restore trace context from the propagated envelope
            if (chatMessage.getTraceId() != null) {
                MDC.put("traceId", chatMessage.getTraceId());
            }
            if (chatMessage.getSpanId() != null) {
                MDC.put("spanId", chatMessage.getSpanId());
            }

            log.debug("Received message from Redis: channel={}, messageId={}, traceId={}",
                    channel, chatMessage.getId(), chatMessage.getTraceId());

            messagingTemplate.convertAndSend(
                    "/topic/stream/" + streamKey,
                    chatMessage
            );

        } catch (Exception e) {
            log.error("Error processing Redis message: {}", e.getMessage(), e);
        } finally {
            // Restore prior MDC state
            if (previousTraceId != null) MDC.put("traceId", previousTraceId);
            else MDC.remove("traceId");
            if (previousSpanId != null) MDC.put("spanId", previousSpanId);
            else MDC.remove("spanId");
        }
    }
}
