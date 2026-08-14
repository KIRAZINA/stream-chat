package com.streamchat.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamAuthorizationServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStreamRoleRepository userStreamRoleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private StreamSettingsRepository streamSettingsRepository;

    @Mock
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private StreamAuthorizationService streamAuthorizationService;

    @BeforeEach
    void setUp() {
        // @InjectMocks uses constructor injection; the @Autowired(required=false)
        // RedisTemplate field is optional and must be wired manually.
        ReflectionTestUtils.setField(streamAuthorizationService, "redisTemplate", redisTemplate);
    }

    @Test
    void canModerate_returnsTrue_forAdmin() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User admin = User.builder().id(200L).username("admin").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(true);

        assertTrue(streamAuthorizationService.canModerate("stream-1", "admin"));
    }

    @Test
    void canModerate_returnsTrue_forOwner() {
        User owner = User.builder().id(100L).username("owner").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(owner)
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRoleRepository.existsByUserIdAndRole(100L, Role.ROLE_ADMIN)).thenReturn(false);

        assertTrue(streamAuthorizationService.canModerate("stream-1", "owner"));
    }

    @Test
    void canModerate_returnsTrue_forModeratorRole() {
        User user = User.builder().id(200L).username("mod").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(user));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertTrue(streamAuthorizationService.canModerate("stream-1", "mod"));
    }

    @Test
    void canModerate_returnsFalse_whenStreamMissing() {
        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.empty());
        assertFalse(streamAuthorizationService.canModerate("stream-1", "user"));
    }

    @Test
    void canModerate_returnsFalse_whenUserMissing() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("user")).thenReturn(Optional.empty());
        assertFalse(streamAuthorizationService.canModerate("stream-1", "user"));
    }

    @Test
    void canManageSettings_returnsTrue_forAdmin() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User admin = User.builder().id(200L).username("admin").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(true);

        assertTrue(streamAuthorizationService.canManageSettings("stream-1", "admin"));
    }

    @Test
    void canManageSettings_returnsTrue_forOwner() {
        User owner = User.builder().id(100L).username("owner").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(owner)
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRoleRepository.existsByUserIdAndRole(100L, Role.ROLE_ADMIN)).thenReturn(false);

        assertTrue(streamAuthorizationService.canManageSettings("stream-1", "owner"));
    }

    @Test
    void canManageSettings_returnsTrue_forBroadcasterRole() {
        User user = User.builder().id(200L).username("broadcaster").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("broadcaster")).thenReturn(Optional.of(user));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamIdAndRole(200L, 10L, Role.ROLE_BROADCASTER))
                .thenReturn(Optional.of(UserStreamRole.builder().build()));

        assertTrue(streamAuthorizationService.canManageSettings("stream-1", "broadcaster"));
    }

    @Test
    void canManageSettings_returnsFalse_whenMissing() {
        assertFalse(streamAuthorizationService.canManageSettings(null, "user"));
        assertFalse(streamAuthorizationService.canManageSettings("stream-1", null));
    }

    @Test
    void assertCanActOn_throws_whenModeratorTargetsBroadcaster() {
        User owner = User.builder().id(100L).username("owner").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(owner)
                .build();
        User moderator = User.builder().id(200L).username("mod").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertThrows(com.streamchat.exception.UnauthorizedException.class,
                () -> streamAuthorizationService.assertCanActOn("stream-1", moderator, owner));
    }

    @Test
    void assertCanActOn_throws_whenModeratorTargetsModerator() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User moderatorA = User.builder().id(200L).username("modA").build();
        User moderatorB = User.builder().id(300L).username("modB").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));
        when(userStreamRoleRepository.findByUserIdAndStreamId(300L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertThrows(com.streamchat.exception.UnauthorizedException.class,
                () -> streamAuthorizationService.assertCanActOn("stream-1", moderatorA, moderatorB));
    }

    @Test
    void assertCanActOn_allowsBroadcasterToTargetModerator() {
        User owner = User.builder().id(100L).username("owner").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(owner)
                .build();
        User moderator = User.builder().id(200L).username("mod").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRoleRepository.existsByUserIdAndRole(100L, Role.ROLE_ADMIN)).thenReturn(false);

        assertDoesNotThrow(
                () -> streamAuthorizationService.assertCanActOn("stream-1", owner, moderator));
    }

    @Test
    void assertCanActOn_throws_whenActorTargetsSelf() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User moderator = User.builder().id(200L).username("mod").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertThrows(com.streamchat.exception.UnauthorizedException.class,
                () -> streamAuthorizationService.assertCanActOn("stream-1", moderator, moderator));

        assertDoesNotThrow(
                () -> streamAuthorizationService.assertCanActOn("stream-1", moderator, moderator, true));
    }

    @Test
    void assertCanActOn_doesNotLeakRolesFromOtherStreams() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();
        User moderator = User.builder().id(200L).username("mod").build();
        User otherBroadcaster = User.builder().id(600L).username("otherBroadcaster").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));
        when(userStreamRoleRepository.findByUserIdAndStreamId(600L, 10L)).thenReturn(List.of());

        assertDoesNotThrow(
                () -> streamAuthorizationService.assertCanActOn("stream-1", moderator, otherBroadcaster));
    }

    @Test
    void assertCanAccessHistory_allows_anyone_whenUnrestricted() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(streamSettingsRepository.findByStreamId(10L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> streamAuthorizationService.assertCanAccessHistory("stream-1", null));
        assertDoesNotThrow(() -> streamAuthorizationService.assertCanAccessHistory("stream-1", "anyone"));
    }

    @Test
    void assertCanAccessHistory_throws_forViewerWhenSubscribersOnly() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).username("owner").build())
                .build();
        User viewer = User.builder().id(300L).username("viewer").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(streamSettingsRepository.findByStreamId(10L))
                .thenReturn(Optional.of(StreamSettings.builder().subscribersOnlyMode(true).build()));
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));
        when(userRoleRepository.existsByUserIdAndRole(300L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(300L, 10L)).thenReturn(List.of());

        assertThrows(com.streamchat.exception.UnauthorizedException.class,
                () -> streamAuthorizationService.assertCanAccessHistory("stream-1", "viewer"));
    }

    @Test
    void assertCanAccessHistory_allows_subscriberWhenSubscribersOnly() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).username("owner").build())
                .build();
        User subscriber = User.builder().id(400L).username("sub").build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(streamSettingsRepository.findByStreamId(10L))
                .thenReturn(Optional.of(StreamSettings.builder().subscribersOnlyMode(true).build()));
        when(userRepository.findByUsername("sub")).thenReturn(Optional.of(subscriber));
        when(userRoleRepository.existsByUserIdAndRole(400L, Role.ROLE_ADMIN)).thenReturn(false);
        when(userStreamRoleRepository.findByUserIdAndStreamId(400L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_SUBSCRIBER).build()));

        assertDoesNotThrow(() -> streamAuthorizationService.assertCanAccessHistory("stream-1", "sub"));
    }

    @Test
    void assertCanAccessHistory_allows_ownerWhenRestricted() {
        User owner = User.builder().id(100L).username("owner").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(owner)
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(streamSettingsRepository.findByStreamId(10L))
                .thenReturn(Optional.of(StreamSettings.builder().followersOnlyMode(true).build()));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRoleRepository.existsByUserIdAndRole(100L, Role.ROLE_ADMIN)).thenReturn(false);

        assertDoesNotThrow(() -> streamAuthorizationService.assertCanAccessHistory("stream-1", "owner"));
    }

    @Test
    void assertCanAccessHistory_throws_forAnonymousWhenRestricted() {
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(streamSettingsRepository.findByStreamId(10L))
                .thenReturn(Optional.of(StreamSettings.builder().subscribersOnlyMode(true).build()));

        assertThrows(com.streamchat.exception.UnauthorizedException.class,
                () -> streamAuthorizationService.assertCanAccessHistory("stream-1", null));
    }

    @Test
    void canModerate_usesCachedRank_whenRedisHit() {
        User user = User.builder().id(200L).username("mod").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(user));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("role:10:200")).thenReturn(3);

        assertTrue(streamAuthorizationService.canModerate("stream-1", "mod"));
        verify(userStreamRoleRepository, never())
                .findByUserIdAndStreamId(anyLong(), anyLong());
    }

    @Test
    void canModerate_cachesViewerRank_onCacheMiss() {
        User user = User.builder().id(200L).username("viewer").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(user));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("role:10:200")).thenReturn(null);

        assertFalse(streamAuthorizationService.canModerate("stream-1", "viewer"));
        verify(valueOperations).set("role:10:200", 0, 300L, TimeUnit.SECONDS);
    }

    @Test
    void canModerate_cachesPrivilegedRank_withShorterTtl() {
        User user = User.builder().id(200L).username("mod").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(user));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("role:10:200")).thenReturn(null);
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertTrue(streamAuthorizationService.canModerate("stream-1", "mod"));
        verify(valueOperations).set("role:10:200", 3, 60L, TimeUnit.SECONDS);
    }

    @Test
    void canModerate_fallsBackToDb_whenRedisFails() {
        User user = User.builder().id(200L).username("mod").build();
        Stream stream = Stream.builder()
                .id(10L)
                .streamKey("stream-1")
                .user(User.builder().id(100L).build())
                .build();

        when(streamRepository.findByStreamKey("stream-1")).thenReturn(Optional.of(stream));
        when(userRepository.findByUsername("mod")).thenReturn(Optional.of(user));
        when(userRoleRepository.existsByUserIdAndRole(200L, Role.ROLE_ADMIN)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));
        when(userStreamRoleRepository.findByUserIdAndStreamId(200L, 10L))
                .thenReturn(List.of(UserStreamRole.builder().role(Role.ROLE_MODERATOR).build()));

        assertTrue(streamAuthorizationService.canModerate("stream-1", "mod"));
    }

    @Test
    void evictRoleCache_deletesKey() {
        streamAuthorizationService.evictRoleCache(10L, 200L);

        verify(redisTemplate).delete("role:10:200");
    }
}
