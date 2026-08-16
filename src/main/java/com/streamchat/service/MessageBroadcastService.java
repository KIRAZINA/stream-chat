package com.streamchat.service;

import com.streamchat.listener.WebSocketPresenceEventListener;
import com.streamchat.model.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Broadcasts a persisted chat message to WebSocket subscribers.
 *
 * <p>Two delivery modes exist:
 * <ul>
 *   <li><b>local-first</b> ({@code chat.broadcast.local-first=true}): the message is
 *       delivered directly via {@link SimpMessagingTemplate} to
 *       {@code /topic/stream/{streamKey}} on this instance with NO Redis round trip.
 *       Valid only under stream affinity, because the sender is subscribed to the
 *       stream and therefore connected to the owner instance.</li>
 *   <li><b>Redis fan-out</b> ({@code chat.broadcast.local-first=false}, the default):
 *       the message is published to the {@code chat:messages:{streamKey}} channel and
 *       {@code RedisMessageSubscriber} (already live) fans it out on every instance.</li>
 * </ul>
 *
 * <p>Message sending is WebSocket-only, so under stream affinity the sender is always
 * on the owner instance. If a REST/async send path is ever added, it must publish via
 * Redis or route to the owner. That is why {@code forceRedis} exists: REST requests can
 * land on any instance, so they must never use local-first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<RedisMessagePublisher> redisMessagePublisherProvider;
    private final WebSocketPresenceEventListener presenceEventListener;
    private final MetricsService metricsService;

    @Value("${chat.broadcast.local-first:false}")
    private boolean localFirst;

    /**
     * Broadcast a message to all subscribers of the stream.
     *
     * @param streamKey  the stream identifier
     * @param dto        the persisted message DTO
     * @param forceRedis when true, always use the Redis fan-out path regardless of
     *                   the local-first flag (for REST sends that may land on any instance)
     */
    public void broadcastMessage(String streamKey, ChatMessageDTO dto, boolean forceRedis) {
        if (!forceRedis && localFirst) {
            broadcastLocal(streamKey, dto);
            return;
        }

        RedisMessagePublisher publisher = redisMessagePublisherProvider.getIfAvailable();
        if (publisher != null && publisher.publish(streamKey, dto)) {
            return;
        }

        // No Redis (dev/test) or publish failed: fall back to the local broker so a
        // single instance still delivers. This mirrors the original ChatController
        // behavior and keeps a no-Redis deployment functional.
        broadcastLocal(streamKey, dto);
    }

    private void broadcastLocal(String streamKey, ChatMessageDTO dto) {
        metricsService.recordBroadcastLocal();
        if (presenceEventListener.getChatSubscriberCount(streamKey) == 0) {
            metricsService.recordBroadcastOrphaned();
            log.warn("Broadcast message to stream {} with zero local subscribers", streamKey);
        }
        messagingTemplate.convertAndSend("/topic/stream/" + streamKey, dto);
    }

    /**
     * Broadcast a presence event (join/leave) to
     * {@code /topic/stream/{streamKey}/events}. Presence events have no Redis
     * channel of their own (only chat messages use {@code chat:messages:*}), so
     * they are always delivered via the local broker, matching the original
     * {@code @SendTo} behavior. Under stream affinity the subscribers are on the
     * owner instance, so a local delivery is sufficient.
     */
    public void broadcastEvent(String streamKey, ChatMessageDTO dto) {
        metricsService.recordBroadcastLocal();
        messagingTemplate.convertAndSend("/topic/stream/" + streamKey + "/events", dto);
    }
}