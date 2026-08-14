package com.streamchat.service;

import com.streamchat.exception.UnauthorizedException;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Authorization helpers for stream-scoped permissions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamAuthorizationService {

    private static final int RANK_VIEWER = 0;
    private static final int RANK_SUBSCRIBER = 1;
    private static final int RANK_VIP = 2;
    private static final int RANK_MODERATOR = 3;
    private static final int RANK_BROADCASTER = 4;

    /**
     * Single role cache, one key per (stream, user). Extends the 1.3 role
     * cache: it stores the resolved stream rank so the per-message privilege
     * check (ChatService.isPrivilegedUser -> canModerate) and the moderation
     * rank comparisons hit Redis instead of UserStreamRoleRepository.
     * A missing key falls back to the DB and is then populated (including the
     * RANK_VIEWER case, so repeat viewers do not keep querying). Invalidation
     * (evictRoleCache) is wired to role add/remove, ban, timeout and unban.
     */
    private static final String ROLE_CACHE_KEY = "role:";
    private static final long ROLE_CACHE_TTL_SECONDS = 300L;
    private static final long ROLE_CACHE_TTL_PRIVILEGED_SECONDS = 60L;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final StreamRepository streamRepository;
    private final UserRepository userRepository;
    private final UserStreamRoleRepository userStreamRoleRepository;
    private final UserRoleRepository userRoleRepository;
    private final StreamSettingsRepository streamSettingsRepository;

    public boolean canModerate(String streamKey, String username) {
        if (username == null || streamKey == null) {
            return false;
        }
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        User user = userRepository.findByUsername(username).orElse(null);
        if (stream == null || user == null) {
            return false;
        }
        if (isAdmin(user.getId())) {
            return true;
        }
        if (stream.getUser().getId().equals(user.getId())) {
            return true;
        }
        return resolveRank(stream, user) >= RANK_MODERATOR;
    }

    public boolean canManageSettings(String streamKey, String username) {
        if (username == null || streamKey == null) {
            return false;
        }
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        User user = userRepository.findByUsername(username).orElse(null);
        if (stream == null || user == null) {
            return false;
        }
        if (isAdmin(user.getId())) {
            return true;
        }
        if (stream.getUser().getId().equals(user.getId())) {
            return true;
        }
        return userStreamRoleRepository
                .findByUserIdAndStreamIdAndRole(user.getId(), stream.getId(), Role.ROLE_BROADCASTER)
                .isPresent();
    }

    private boolean isAdmin(Long userId) {
        return userRoleRepository.existsByUserIdAndRole(userId, Role.ROLE_ADMIN);
    }

    /**
     * Ensure the actor may take a moderation action against the target.
     * Targets with an equal or higher stream role than the actor are
     * off-limits (moderator cannot moderate another moderator or the
     * broadcaster; the broadcaster cannot act on themselves).
     */
    public void assertCanActOn(String streamKey, User actor, User target) {
        assertCanActOn(streamKey, actor, target, false);
    }

    /**
     * Same as {@link #assertCanActOn(String, User, User)} but allows the
     * actor to act on their own record (e.g. deleting their own message).
     */
    public void assertCanActOn(String streamKey, User actor, User target, boolean allowSelf) {
        if (streamKey == null || actor == null || target == null) {
            return;
        }
        if (allowSelf && actor.getId().equals(target.getId())) {
            return;
        }
        if (isAdmin(actor.getId())) {
            return;
        }
        int actorRank = resolveRank(streamKey, actor);
        int targetRank = resolveRank(streamKey, target);
        if (targetRank >= actorRank) {
            throw new UnauthorizedException(
                    "Cannot moderate a user with equal or higher role");
        }
    }

    private int resolveRank(String streamKey, User user) {
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        return resolveRank(stream, user);
    }

    private int resolveRank(Stream stream, User user) {
        if (stream == null) {
            return RANK_VIEWER;
        }
        if (stream.getUser().getId().equals(user.getId())) {
            return RANK_BROADCASTER;
        }
        Integer cached = getCachedRank(stream.getId(), user.getId());
        if (cached != null) {
            return cached;
        }
        int rank = userStreamRoleRepository.findByUserIdAndStreamId(user.getId(), stream.getId())
                .stream()
                .map(UserStreamRole::getRole)
                .mapToInt(this::rankOf)
                .max()
                .orElse(RANK_VIEWER);
        putCachedRank(stream.getId(), user.getId(), rank);
        return rank;
    }

    private Integer getCachedRank(Long streamId, Long userId) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object cached = redisTemplate.opsForValue()
                    .get(ROLE_CACHE_KEY + streamId + ":" + userId);
            if (cached == null) {
                return null;
            }
            if (cached instanceof Number number) {
                return number.intValue();
            }
            if (cached instanceof String text) {
                return Integer.parseInt(text);
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to read role cache, falling back to DB: {}", e.getMessage());
            return null;
        }
    }

    private void putCachedRank(Long streamId, Long userId, int rank) {
        if (redisTemplate == null) {
            return;
        }
        try {
            long ttl = rank > RANK_VIEWER
                    ? ROLE_CACHE_TTL_PRIVILEGED_SECONDS
                    : ROLE_CACHE_TTL_SECONDS;
            redisTemplate.opsForValue().set(
                    ROLE_CACHE_KEY + streamId + ":" + userId,
                    rank,
                    ttl,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to write role cache: {}", e.getMessage());
        }
    }

    /**
     * Remove the role cache entry for a (stream, user). Called whenever the
     * user's stream role can change: role add/remove, ban, timeout, unban.
     * Missing roles are cached as RANK_VIEWER, so eviction is what makes
     * privilege changes visible promptly.
     */
    public void evictRoleCache(Long streamId, Long userId) {
        if (streamId == null || userId == null) {
            return;
        }
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(ROLE_CACHE_KEY + streamId + ":" + userId);
        } catch (Exception e) {
            log.warn("Failed to evict role cache: {}", e.getMessage());
        }
    }

    private int rankOf(Role role) {
        switch (role) {
            case ROLE_BROADCASTER:
                return RANK_BROADCASTER;
            case ROLE_MODERATOR:
                return RANK_MODERATOR;
            case ROLE_VIP:
                return RANK_VIP;
            case ROLE_SUBSCRIBER:
                return RANK_SUBSCRIBER;
            default:
                return RANK_VIEWER;
        }
    }

    /**
     * Ensure the user may read chat history for a stream.
     * When neither subscribers-only nor followers-only mode is active the
     * history is public. Otherwise an authenticated user with a stream role
     * (subscriber or above) is required; admins and the stream owner always
     * pass.
     */
    public void assertCanAccessHistory(String streamKey, String username) {
        if (streamKey == null) {
            return;
        }
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        if (stream == null) {
            return;
        }
        StreamSettings settings = streamSettingsRepository.findByStreamId(stream.getId()).orElse(null);
        boolean restricted = settings != null
                && (Boolean.TRUE.equals(settings.getSubscribersOnlyMode())
                    || Boolean.TRUE.equals(settings.getFollowersOnlyMode()));
        if (!restricted) {
            return;
        }
        if (username == null) {
            throw new UnauthorizedException(
                    "Authentication required to read chat history for this stream");
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            throw new UnauthorizedException(
                    "Authentication required to read chat history for this stream");
        }
        if (isAdmin(user.getId())) {
            return;
        }
        if (stream.getUser().getId().equals(user.getId())) {
            return;
        }
        if (resolveRank(streamKey, user) > RANK_VIEWER) {
            return;
        }
        throw new UnauthorizedException(
                "You must follow or subscribe to read chat history for this stream");
    }
}
