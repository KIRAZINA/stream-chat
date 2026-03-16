package com.streamchat.service;

import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserRoleRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @InjectMocks
    private StreamAuthorizationService streamAuthorizationService;

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
        when(userStreamRoleRepository.hasModeratorRole(10L, 200L)).thenReturn(true);

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
}
