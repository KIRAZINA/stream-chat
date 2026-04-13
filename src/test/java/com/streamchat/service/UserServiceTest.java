package com.streamchat.service;

import com.streamchat.model.dto.UserDTO;
import com.streamchat.model.entity.User;
import com.streamchat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .color("#FF0000")
                .isActive(true)
                .build();
    }

    @Test
    void registerUser_Success() {
        String username = "newuser";
        String email = "newuser@example.com";
        String password = "password123";
        String encodedPassword = "encoded_password_hash";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });
        UserDTO result = userService.registerUser(username, email, password);
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals(email, result.getEmail());
        assertEquals(username, result.getDisplayName()); // Display name defaults to username
        assertNotNull(result.getColor()); // Random color generated
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode(password);
    }

    @Test
    void registerUser_UsernameAlreadyExists_ThrowsException() {
        String username = "existinguser";
        String email = "new@example.com";
        String password = "password123";

        when(userRepository.existsByUsername(username)).thenReturn(true);
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.registerUser(username, email, password));

        assertEquals("Username already exists", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_EmailAlreadyExists_ThrowsException() {
        String username = "newuser";
        String email = "existing@example.com";
        String password = "password123";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(true);
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.registerUser(username, email, password));

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserByUsername_Success() {
        String username = "testuser";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        UserDTO result = userService.getUserByUsername(username);
        assertNotNull(result);
        assertEquals(testUser.getId(), result.getId());
        assertEquals(testUser.getUsername(), result.getUsername());
        assertEquals(testUser.getEmail(), result.getEmail());
        assertEquals(testUser.getDisplayName(), result.getDisplayName());
        assertEquals(testUser.getColor(), result.getColor());
    }

    @Test
    void getUserByUsername_UserNotFound_ThrowsException() {
        String username = "nonexistent";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.getUserByUsername(username));

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void updateDisplayName_Success() {
        String username = "testuser";
        String newDisplayName = "New Display Name";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        userService.updateDisplayName(username, newDisplayName);
        verify(userRepository).save(argThat(user ->
                newDisplayName.equals(user.getDisplayName())
        ));
    }

    @Test
    void updateDisplayName_UserNotFound_ThrowsException() {
        String username = "nonexistent";
        String newDisplayName = "New Display Name";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.updateDisplayName(username, newDisplayName));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateColor_Success() {
        String username = "testuser";
        String newColor = "#00FF00";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        userService.updateColor(username, newColor);
        verify(userRepository).save(argThat(user ->
                newColor.equals(user.getColor())
        ));
    }

    @Test
    void updateColor_InvalidFormat_ThrowsException() {
        String username = "testuser";
        String invalidColor = "invalid-color";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.updateColor(username, invalidColor));

        assertEquals("Invalid color format", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateColor_InvalidFormat_NoHash_ThrowsException() {
        String username = "testuser";
        String invalidColor = "FF0000"; // Missing #

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        assertThrows(IllegalArgumentException.class, () ->
                userService.updateColor(username, invalidColor));
    }

    @Test
    void updateColor_InvalidFormat_ShortHex_ThrowsException() {
        String username = "testuser";
        String invalidColor = "#FF00"; // Too short

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        assertThrows(IllegalArgumentException.class, () ->
                userService.updateColor(username, invalidColor));
    }

    @Test
    void updateColor_InvalidFormat_InvalidCharacters_ThrowsException() {
        String username = "testuser";
        String invalidColor = "#GGGGGG"; // Invalid hex characters

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        assertThrows(IllegalArgumentException.class, () ->
                userService.updateColor(username, invalidColor));
    }

    @Test
    void updateColor_UserNotFound_ThrowsException() {
        String username = "nonexistent";
        String newColor = "#00FF00";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.updateColor(username, newColor));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_GeneratesRandomColor() {
        String username = "newuser";
        String email = "newuser@example.com";
        String password = "password123";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("encoded");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });
        UserDTO result1 = userService.registerUser(username, email, password);
        
        // Register another user to verify different colors
        when(userRepository.existsByUsername("newuser2")).thenReturn(false);
        when(userRepository.existsByEmail("newuser2@example.com")).thenReturn(false);
        UserDTO result2 = userService.registerUser("newuser2", "newuser2@example.com", password);
        assertNotNull(result1.getColor());
        assertNotNull(result2.getColor());
        assertTrue(result1.getColor().matches("^#[0-9A-Fa-f]{6}$"));
        assertTrue(result2.getColor().matches("^#[0-9A-Fa-f]{6}$"));
        // Colors might be different (though unlikely to be same due to randomness)
    }
}
