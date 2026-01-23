package com.streamchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Stream Chat Platform.
 *
 * This application provides a real-time chat system for live streaming
 * with features including:
 * - WebSocket-based real-time messaging
 * - User authentication and authorization
 * - Moderation tools (timeout, ban, message deletion)
 * - Rate limiting and spam protection
 * - Custom emotes support
 * - Horizontal scaling with Redis pub/sub
 */
@SpringBootApplication
@EnableScheduling
public class StreamChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamChatApplication.class, args);
    }
}