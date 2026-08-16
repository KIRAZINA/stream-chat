package com.streamchat.integration;

import com.streamchat.model.entity.User;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.service.StreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track A · Item 1 (Stream Affinity): integration tests for the native
 * WebSocket endpoint /ws-chat/stream/{streamKey}.
 *
 * - A valid stream key establishes a WebSocket upgrade (raw WS + STOMP).
 * - A nonexistent stream key is rejected at the handshake.
 * - The legacy /ws-chat SockJS endpoint still accepts STOMP CONNECT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class StreamAffinityEndpointIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StreamService streamService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private StreamSettingsRepository streamSettingsRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private String streamKey;
    private String token;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("affinity-user")
                .email("affinity@example.com")
                .passwordHash("password123")
                .build());

        streamKey = streamService.createStream("affinity-user", "Affinity Test", "test").getStreamKey();
        token = tokenProvider.generateToken("affinity-user");
    }

    @Test
    void validStreamKey_establishesRawWebSocketUpgrade() throws Exception {
        WebSocketSession session = openRaw(streamKey);
        assertTrue(session.isOpen(), "raw WebSocket upgrade to a valid stream key must succeed");
        session.close();
    }

    @Test
    void missingStreamKey_isRejectedAtHandshake() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();

        try {
            client.execute(new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) {
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) {
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws-chat/stream/no-such-stream"))
                    .get(5, TimeUnit.SECONDS);
            throw new AssertionError("handshake to a nonexistent stream must be rejected");
        } catch (ExecutionException expected) {
            // the upgrade was refused — exactly what the interceptor should do
        }
    }

    @Test
    void validStreamKey_stompConnectAndSubscribeWork() throws Exception {
        StompSession session = connectStomp(streamKey);
        assertTrue(session.isConnected(), "STOMP session over stream-keyed endpoint must connect");
        session.subscribe("/topic/stream/" + streamKey,
                new org.springframework.messaging.simp.stomp.StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders headers, Object payload) {
                    }
                });
        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    void legacyWsChat_stillAcceptsConnections() throws Exception {
        StompSession session = connectStompLegacy();
        assertTrue(session.isConnected(), "legacy /ws-chat SockJS endpoint must still accept STOMP CONNECT");
        session.disconnect();
    }

    private WebSocketSession openRaw(String key) throws Exception {
        CompletableFuture<WebSocketSession> established = new CompletableFuture<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        client.execute(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                established.complete(session);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws-chat/stream/" + key))
                .get(5, TimeUnit.SECONDS);
        return established.get(5, TimeUnit.SECONDS);
    }

    private StompSession connectStomp(String key) throws Exception {
        org.springframework.web.socket.messaging.WebSocketStompClient client =
                new org.springframework.web.socket.messaging.WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setDefaultHeartbeat(new long[]{0, 0});

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + token);

        return client.connectAsync(
                        "ws://localhost:" + port + "/ws-chat/stream/" + key,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private StompSession connectStompLegacy() throws Exception {
        org.springframework.web.socket.messaging.WebSocketStompClient client =
                new org.springframework.web.socket.messaging.WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setDefaultHeartbeat(new long[]{0, 0});

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + token);

        return client.connectAsync(
                        "ws://localhost:" + port + "/ws-chat/websocket",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }
}