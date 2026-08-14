package com.streamchat.service;

import com.streamchat.model.entity.BannedUser;
import com.streamchat.model.entity.TimedOutUser;
import com.streamchat.repository.BannedUserRepository;
import com.streamchat.repository.TimedOutUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists moderation inserts in a REQUIRES_NEW transaction.
 *
 * The INSERT must never run inside the caller's transaction: when a concurrent
 * ban/timeout violates the UNIQUE(stream_id, user_id) constraint, the JPA
 * provider marks that transaction rollback-only, so an in-transaction
 * catch-and-recover can never commit. Isolating the insert here lets the
 * caller recover by re-finding the winning row while its own (read-only)
 * transaction stays clean.
 */
@Service
@RequiredArgsConstructor
public class ModerationPersister {

    private final BannedUserRepository bannedUserRepository;
    private final TimedOutUserRepository timedOutUserRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BannedUser insertBan(BannedUser ban) {
        return bannedUserRepository.saveAndFlush(ban);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TimedOutUser insertTimeout(TimedOutUser timeout) {
        return timedOutUserRepository.saveAndFlush(timeout);
    }
}