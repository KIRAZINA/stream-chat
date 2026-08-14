package com.streamchat.integration;

import com.streamchat.model.entity.User;
import com.streamchat.security.JwtTokenProvider;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
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
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.1 WebSocket hardening integration tests against the real broker:
 * - an oversized STOMP frame (> 64 KiB incoming limit) closes the session;
 * - a session whose client never sends frames is reaped by the heartbeat
 *   timeout (server interval shortened to 1s via test property).
 * The CONNECT path exercises the JWT interceptor end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.websocket.heartbeat-interval-ms=1000")
@ActiveProfiles("dev")
class WebSocketConfigIntegrationTest {

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
                .username("ws-user")
                .email("ws@example.com")
                .passwordHash("password123")
                .build());

        streamKey = streamService.createStream("ws-user", "WS Test", "test").getStreamKey();
        token = tokenProvider.generateToken("ws-user");
    }

    @Test
    void oversizedMessage_isRejected_sessionClosed() throws Exception {
        StompSession session = connect();

        String oversized = "x".repeat(70 * 1024);
        try {
            session.send("/app/chat.send/" + streamKey, oversized);
        } catch (Exception ignored) {
            // the transport may close mid-send; that is the expected outcome
        }

        assertTrue(awaitTrue(() -> !session.isConnected(), 10),
                "session must be closed after sending a frame over the incoming size limit");
    }

    @Test
    void idleSession_withoutHeartbeats_isReaped() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        WebSocketSession ws = connectSilent(closed);
        assertTrue(ws.isOpen(), "fresh session must be open");

        // The raw client advertises heart-beat:1000,1000 in its STOMP CONNECT
        // but then sends nothing. The simple broker's read timeout is
        // 3 x max(clientSend, serverReceive) = 3s with the 1s test interval,
        // so the server must close the silent session shortly after.
        assertTrue(awaitTrue(closed::get, 15),
                "idle session must be reaped by the heartbeat timeout");
        assertFalse(ws.isOpen());
    }

    private StompSession connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        // [0,0] needs no client TaskScheduler and is fine here: this test only
        // exercises the incoming size limit, not heartbeat reaping.
        client.setDefaultHeartbeat(new long[]{0, 0});

        // Authorization must be a STOMP CONNECT frame header: the inbound
        // interceptor reads it via getFirstNativeHeader("Authorization").
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + token);

        return client.connectAsync(
                        "ws://localhost:" + port + "/ws-chat/websocket",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private WebSocketSession connectSilent(AtomicBoolean closed) throws Exception {
        CompletableFuture<WebSocketSession> established = new CompletableFuture<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        client.execute(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                established.complete(session);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                // CONNECTED frame (and any server heartbeats): ignored.
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                closed.set(true);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws-chat/websocket"))
                .get(10, TimeUnit.SECONDS);

        WebSocketSession session = established.get(10, TimeUnit.SECONDS);
        String connectFrame = "CONNECT\naccept-version:1.2\nheart-beat:1000,1000\n"
                + "Authorization:Bearer " + token + "\n\n\u0000";
        session.sendMessage(new TextMessage(connectFrame));
        return session;
    }

    private static boolean awaitTrue(Condition condition, long seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.eval()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.eval();
    }

    @FunctionalInterface
    private interface Condition {
        boolean eval() throws InterruptedException;
    }
}