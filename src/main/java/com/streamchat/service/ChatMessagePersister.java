package com.streamchat.service;

import com.streamchat.model.entity.ChatMessage;
import com.streamchat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists chat messages in a REQUIRES_NEW transaction.
 *
 * The message INSERT must never run inside the caller's transaction: when a
 * duplicate idempotency_key violates the (global) unique index, the JPA
 * provider marks that transaction rollback-only, so an in-transaction
 * catch-and-recover can never commit. Isolating the insert here lets the
 * caller recover by re-finding the winning row while its own (read-only)
 * transaction stays clean.
 */
@Service
@RequiredArgsConstructor
public class ChatMessagePersister {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage persist(ChatMessage message) {
        return chatMessageRepository.saveAndFlush(message);
    }
}