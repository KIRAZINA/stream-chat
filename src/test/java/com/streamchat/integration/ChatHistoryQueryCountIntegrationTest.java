package com.streamchat.integration;

import com.streamchat.model.entity.ChatMessage;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.enums.MessageType;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.service.ChatService;
import com.streamchat.service.StreamService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies 2.6: reply-preview resolution on history reads is batched.
 *
 * Two streams with a 30-message page are compared: one with zero replies and
 * one with 20 replies. The reply-heavy page must cost exactly ONE extra query
 * (the single findAllById batch), proving query count is bounded regardless
 * of reply density. Both pages share identical per-message lazy-loading and
 * badge/role noise, so the delta isolates the reply-resolution cost.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ChatHistoryQueryCountIntegrationTest {

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private SessionFactory sessionFactory;
    private String noReplyStreamKey;
    private String replyStreamKey;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        streamSettingsRepository.deleteAll();
        streamRepository.deleteAll();
        userRepository.deleteAll();

        sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);

        userRepository.save(User.builder()
                .username("history-user")
                .email("history@example.com")
                .passwordHash("password123")
                .build());

        noReplyStreamKey = streamService.createStream("history-user", "No Reply", "test").getStreamKey();
        replyStreamKey = streamService.createStream("history-user", "With Reply", "test").getStreamKey();

        Stream noReplyStream = streamRepository.findByStreamKey(noReplyStreamKey).get();
        for (int i = 0; i < 30; i++) {
            saveMessage(noReplyStream, "plain-" + i, null);
        }

        Stream replyStream = streamRepository.findByStreamKey(replyStreamKey).get();
        List<ChatMessage> targets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            targets.add(saveMessage(replyStream, "target-" + i, null));
        }
        for (int i = 0; i < 20; i++) {
            saveMessage(replyStream, "reply-" + i, targets.get(i).getId());
        }
        for (int i = 0; i < 10; i++) {
            saveMessage(replyStream, "plain-" + i, null);
        }
    }

    @Test
    @Transactional
    void historyPageWithReplies_costsExactlyOneExtraQuery() {
        long q0 = measure(noReplyStreamKey);
        long q1 = measure(replyStreamKey);

        assertEquals(q0 + 1, q1,
                "batch reply loading must add exactly one query (findAllById)");
        assertTrue(q1 <= 100,
                "total query count must stay bounded regardless of reply density");
    }

    private long measure(String streamKey) {
        sessionFactory.getStatistics().clear();
        chatService.getMessageHistory(streamKey, null, 30);
        return sessionFactory.getStatistics().getPrepareStatementCount();
    }

    private ChatMessage saveMessage(Stream stream, String content, Long replyToMessageId) {
        User author = userRepository.findByUsername("history-user").get();
        return chatMessageRepository.save(ChatMessage.builder()
                .stream(stream)
                .user(author)
                .username("history-user")
                .content(content)
                .replyToMessageId(replyToMessageId)
                .messageType(MessageType.CHAT)
                .build());
    }
}