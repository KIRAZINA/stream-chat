package com.streamchat.service;

import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the 2.7a role cache against a real Redis server: first resolution
 * consults the (mocked) DB and populates the cache, subsequent resolutions
 * short-circuit via Redis (no UserStreamRoleRepository call), and eviction
 * forces the next resolution back to the DB. Skipped when Docker is
 * unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class RoleCacheRedisIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    private static RedisTemplate<String, Object> redisTemplate;

    private final StreamRepository streamRepository = mock(StreamRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserStreamRoleRepository userStreamRoleRepository = mock(UserStreamRoleRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final StreamSettingsRepository streamSettingsRepository = mock(StreamSettingsRepository.class);

    private final StreamAuthorizationService service;

    RoleCacheRedisIntegrationTest() {
        service = new StreamAuthorizationService(
                streamRepository, userRepository, userStreamRoleRepository,
                userRoleRepository, streamSettingsRepository);
    }

    @BeforeEach
    void wireRedis() {
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
    }

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("role:10:200");
        redisTemplate.delete("role:10:300");
    }

    @Test
    void secondResolutionHitsCache_NoRoleQuery() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User moderator = User.builder().id(200L).username("mod").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(moderator));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertTrue(service.canModerate("stream-1", "mod"), "first resolution must consult DB");
        verify(userStreamRoleRepository).findByUserIdAndStreamId(200L, 10L);

        clearInvocations(userStreamRoleRepository);

        assertTrue(service.canModerate("stream-1", "mod"), "second resolution must hit Redis");
        verify(userStreamRoleRepository, never()).findByUserIdAndStreamId(anyLong(), anyLong());
    }

    @Test
    void evictionForcesDbRefresh() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User moderator = User.builder().id(200L).username("mod").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(moderator));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertTrue(service.canModerate("stream-1", "mod"));
        clearInvocations(userStreamRoleRepository);

        service.evictRoleCache(10L, 200L);
        assertTrue(service.canModerate("stream-1", "mod"));
        verify(userStreamRoleRepository).findByUserIdAndStreamId(200L, 10L);
    }

    @Test
    void viewerRankIsNegativelyCached() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User viewer = User.builder().id(300L).username("viewer").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));
        when(userRoleRepository.existsByUserIdAndRole(300L, Role.ROLE_ADMIN)).thenReturn(false);

        assertFalse(service.canModerate("stream-1", "viewer"), "first resolution must consult DB");
        clearInvocations(userStreamRoleRepository);

        assertFalse(service.canModerate("stream-1", "viewer"), "second resolution must hit Redis");
        verify(userStreamRoleRepository, never()).findByUserIdAndStreamId(anyLong(), anyLong());
    }
}