package com.streamchat.integration;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.StreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track A · Item 1 (Local-first broadcast): end-to-end STOMP test that the
 * WebSocket send path works, because the whole feature was silently broken by
 * the a790782/4cdf2c8 batch (no @MessageMapping handler existed).
 *
 * Sends /app/chat.send/{streamKey} from one client and asserts BOTH the sender
 * and a second subscribed client receive the broadcast, and that the message was
 * persisted. Runs under both chat.broadcast.local-first=false (default, Redis
* fan-out) and true (local broker). The two concrete subclasses pin the two modes.
 */
abstract class ChatWebSocketE2EBaseTest {

    @LocalServerPort
    private int port;

    @Autowired
    protected StreamService streamService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ChatMessageRepository chatMessageRepository;

    @Autowired
    protected StreamSettingsRepository streamSettingsRepository;

    @Autowired
    protected StreamRepository streamRepository;

    @Autowired
    protected JwtTokenProvider tokenProvider;

    protected String streamKey;
    protected String token;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("e2e-user")
                .email("e2e@example.com")
                .passwordHash("password123")
                .build());

        streamKey = streamService.createStream("e2e-user", "E2E Test", "test").getStreamKey();
        token = tokenProvider.generateToken("e2e-user");
    }

    @Test
    void sendMessage_overStomp_isPersistedAndBroadcastToBothClients() throws Exception {
        StompSession sender = connect();
        StompSession second = connect();

        BlockingQueue<ChatMessageDTO> senderInbox = new LinkedBlockingQueue<>();
        BlockingQueue<ChatMessageDTO> secondInbox = new LinkedBlockingQueue<>();
        BlockingQueue<ChatMessageDTO> senderErrors = new LinkedBlockingQueue<>();
        sender.subscribe("/topic/stream/" + streamKey, frameHandler(senderInbox));
        second.subscribe("/topic/stream/" + streamKey, frameHandler(secondInbox));
        sender.subscribe("/user/queue/errors", frameHandler(senderErrors));

        String idempotencyKey = "e2e-" + System.nanoTime();
        sender.send("/app/chat.send/" + streamKey,
                Map.of("content", "Hello e2e", "idempotencyKey", idempotencyKey));

        ChatMessageDTO senderGot = senderInbox.poll(10, TimeUnit.SECONDS);
        ChatMessageDTO secondGot = secondInbox.poll(10, TimeUnit.SECONDS);

        ChatMessageDTO error = senderErrors.poll(2, TimeUnit.SECONDS);
        if (error != null) {
            throw new AssertionError("send failed: " + error.getContent());
        }
        assertNotNull(senderGot, "sender must receive its own broadcast");
        assertNotNull(secondGot, "a second subscriber must receive the broadcast");
        assertEquals("Hello e2e", senderGot.getContent());
        assertEquals("Hello e2e", secondGot.getContent());
        assertNotNull(senderGot.getId(), "broadcast must carry the persisted message id");
        assertNotNull(senderGot.getTimestamp(), "broadcast must carry a timestamp");
        Duration skew = Duration.between(senderGot.getTimestamp(), OffsetDateTime.now()).abs();
        assertTrue(skew.toMinutes() < 5,
                "broadcast timestamp must be an absolute instant near now, skew=" + skew);

        long persisted = chatMessageRepository.findAll().stream()
                .filter(m -> idempotencyKey.equals(m.getIdempotencyKey()))
                .count();
        assertEquals(1L, persisted, "message must be persisted to the database");

        sender.disconnect();
        second.disconnect();
    }

@Test
    void joinEvent_isBroadcastToSubscribers() throws Exception {
        StompSession listener = connect();

        BlockingQueue<ChatMessageDTO> inbox = new LinkedBlockingQueue<>();
        listener.subscribe("/topic/stream/" + streamKey + "/events", frameHandler(inbox));

        StompSession joiner = connect();
        joiner.send("/app/chat.join/" + streamKey, Map.of("streamKey", streamKey));

        ChatMessageDTO event = inbox.poll(10, TimeUnit.SECONDS);
        assertNotNull(event, "join event must be delivered to /events subscribers");
        assertEquals("JOIN", event.getMessageType().name());

        joiner.disconnect();
        listener.disconnect();
    }

    @Test
    void slowMode_rejectsRapidSecondMessageFromViewer() throws Exception {
        userRepository.save(User.builder()
                .username("viewer")
                .email("viewer@example.com")
                .passwordHash("password123")
                .build());

        long streamId = streamRepository.findByStreamKey(streamKey).orElseThrow().getId();
        StreamSettings settings = streamSettingsRepository.findByStreamId(streamId)
                .orElseThrow(() -> new IllegalStateException("settings row must exist after stream creation"));
        settings.setSlowModeEnabled(true);
        settings.setSlowModeSeconds(10);
        streamSettingsRepository.save(settings);

        StompSession viewer = connectWithToken(tokenProvider.generateToken("viewer"));

        BlockingQueue<ChatMessageDTO> inbox = new LinkedBlockingQueue<>();
        BlockingQueue<ChatMessageDTO> errors = new LinkedBlockingQueue<>();
        viewer.subscribe("/topic/stream/" + streamKey, frameHandler(inbox));
        viewer.subscribe("/user/queue/errors", frameHandler(errors));

        viewer.send("/app/chat.send/" + streamKey,
                Map.of("content", "one", "idempotencyKey", "slow-1"));
        assertNotNull(inbox.poll(10, TimeUnit.SECONDS), "first message must be accepted and broadcast");

        viewer.send("/app/chat.send/" + streamKey,
                Map.of("content", "two", "idempotencyKey", "slow-2"));

        ChatMessageDTO error = errors.poll(10, TimeUnit.SECONDS);
        assertNotNull(error, "rapid second message must be rejected with an error on the sender queue");
        assertEquals(MessageType.ERROR, error.getMessageType());
        assertTrue(error.getContent().contains("Slow mode"));
        assertEquals("slow-2", error.getIdempotencyKey(),
                "error frame must echo the idempotencyKey of the rejected message");

        ChatMessageDTO stray = inbox.poll(2, TimeUnit.SECONDS);
        assertNull(stray, "rejected message must not be broadcast to subscribers");

        long persisted = chatMessageRepository.findAll().stream()
                .filter(m -> "slow-1".equals(m.getIdempotencyKey()) || "slow-2".equals(m.getIdempotencyKey()))
                .count();
        assertEquals(1L, persisted, "rejected message must not be persisted");

        viewer.disconnect();
    }

    protected StompSession connect() throws Exception {
        return connectWithToken(token);
    }

    protected StompSession connectWithToken(String authToken) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        client.setMessageConverter(converter);
        client.setDefaultHeartbeat(new long[]{0, 0});

StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + authToken);

        return client.connectAsync(
                        "ws://localhost:" + port + "/ws-chat/stream/" + streamKey,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

private static StompFrameHandler frameHandler(BlockingQueue<ChatMessageDTO> inbox) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.offer((ChatMessageDTO) payload);
            }
        };
    }
}
