package com.streamchat.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chat.broadcast.local-first=true")
@ActiveProfiles("dev")
class ChatWebSocketLocalFirstE2ETest extends ChatWebSocketE2EBaseTest {
}