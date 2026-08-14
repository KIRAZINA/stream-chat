package com.streamchat.integration;

import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.dto.StreamDTO;
import com.streamchat.model.entity.ChatMessage;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.service.ChatService;
import com.streamchat.service.StreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for V7 idempotent message publishing.
 *
 * Exercises the real H2 database + EntityManager, so the unique index on
 * idempotency_key and the race-recovery path (saveAndFlush + detach + re-find)
 * are verified against a genuine constraint violation, not a mock.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=32")
class ChatIdempotencyIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private StreamService streamService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private StreamSettingsRepository streamSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    private String streamKey;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("idem-user")
                .email("idem@example.com")
                .passwordHash("password123")
                .build());

        streamKey = streamService.createStream("idem-user", "Idempotency Test", "test").getStreamKey();
    }

    @Test
    void parallelSends_sameKey_produceOneRowAndOneMessageId() throws Exception {
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        List<Future<ChatMessageDTO>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(10, TimeUnit.SECONDS);
                try {
                    return chatService.sendMessage(streamKey, "idem-user", "Hello world",
                            MessageType.CHAT, null, "parallel-key-1");
                } catch (Exception e) {
                    failures.incrementAndGet();
                    throw e;
                }
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        List<ChatMessageDTO> results = new ArrayList<>();
        for (Future<ChatMessageDTO> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, failures.get(), "no send should error out");
        assertEquals(1, results.stream().map(ChatMessageDTO::getId).distinct().count(),
                "all parallel sends must resolve to the same message id");
        assertEquals(1, countRowsWithKey("parallel-key-1"),
                "exactly one row must exist for the shared idempotency key");
    }

    @Test
    void sequentialRetry_sameKey_returnsSameMessage() {
        ChatMessageDTO first = chatService.sendMessage(streamKey, "idem-user", "Hello",
                MessageType.CHAT, null, "seq-key-1");
        ChatMessageDTO second = chatService.sendMessage(streamKey, "idem-user", "Hello",
                MessageType.CHAT, null, "seq-key-1");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, countRowsWithKey("seq-key-1"));
    }

    @Test
    void nullIdempotencyKey_createsDistinctRows() {
        ChatMessageDTO first = chatService.sendMessage(streamKey, "idem-user", "Message A",
                MessageType.CHAT, null, null);
        ChatMessageDTO second = chatService.sendMessage(streamKey, "idem-user", "Message B",
                MessageType.CHAT, null, null);

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, chatMessageRepository.findAll().size());
    }

    @Test
    void crossStream_sameKey_rejected() {
        userRepository.save(User.builder()
                .username("idem-owner2")
                .email("idem2@example.com")
                .passwordHash("password123")
                .build());
        StreamDTO secondStream = streamService.createStream("idem-owner2", "Second Stream", "test");

        chatService.sendMessage(streamKey, "idem-user", "Hello",
                MessageType.CHAT, null, "cross-key-1");

        assertThrows(DataIntegrityViolationException.class, () ->
                chatService.sendMessage(secondStream.getStreamKey(), "idem-owner2", "Hello",
                        MessageType.CHAT, null, "cross-key-1"));

        assertEquals(1, countRowsWithKey("cross-key-1"));
    }

    private long countRowsWithKey(String key) {
        return chatMessageRepository.findAll().stream()
                .map(ChatMessage::getIdempotencyKey)
                .filter(key::equals)
                .count();
    }
}
