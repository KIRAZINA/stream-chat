package com.streamchat.service;

import com.streamchat.model.dto.StreamDTO;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StreamService.
 */
@ExtendWith(MockitoExtension.class)
class StreamServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private StreamSettingsRepository streamSettingsRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StreamService streamService;

    private User testUser;
    private Stream testStream;
    private StreamSettings testSettings;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("streamer")
                .email("streamer@example.com")
                .build();

        testStream = Stream.builder()
                .id(1L)
                .streamKey("test-stream-key")
                .user(testUser)
                .title("Test Stream")
                .description("Test Description")
                .isLive(false)
                .viewerCount(0)
                .build();

        testSettings = StreamSettings.builder()
                .id(1L)
                .stream(testStream)
                .build();

        testStream.setSettings(testSettings);
    }

    @Test
    void createStream_Success() {
        // Arrange
        String username = "streamer";
        String title = "My Stream";
        String description = "Stream Description";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(streamRepository.existsByStreamKey(anyString())).thenReturn(false);
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> {
                    Stream stream = invocation.getArgument(0);
                    stream.setId(1L);
                    return stream;
                });
        when(streamSettingsRepository.save(any(StreamSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        StreamDTO result = streamService.createStream(username, title, description);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getStreamKey());
        assertEquals(title, result.getTitle());
        assertEquals(description, result.getDescription());
        assertFalse(result.getIsLive());
        assertEquals(testUser.getId(), result.getUserId());
        verify(streamRepository).save(any(Stream.class));
        verify(streamSettingsRepository).save(any(StreamSettings.class));
    }

    @Test
    void createStream_UserNotFound_ThrowsException() {
        // Arrange
        String username = "nonexistent";
        String title = "My Stream";
        String description = "Stream Description";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.createStream(username, title, description));

        assertEquals("User not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void createStream_GeneratesUniqueStreamKey() {
        // Arrange
        String username = "streamer";
        String title = "My Stream";
        String description = "Stream Description";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(testUser));
        when(streamRepository.existsByStreamKey(anyString()))
                .thenReturn(false, false); // First two keys don't exist
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> {
                    Stream stream = invocation.getArgument(0);
                    stream.setId(1L);
                    return stream;
                });
        when(streamSettingsRepository.save(any(StreamSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        StreamDTO result = streamService.createStream(username, title, description);

        // Assert
        assertNotNull(result.getStreamKey());
        assertEquals(16, result.getStreamKey().length()); // UUID substring length
        verify(streamRepository).save(any(Stream.class));
    }

    @Test
    void startStream_Success() {
        // Arrange
        String streamKey = "test-stream-key";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        streamService.startStream(streamKey, username);

        // Assert
        verify(streamRepository).save(argThat(stream ->
                stream.getIsLive() && stream.getStartedAt() != null
        ));
    }

    @Test
    void startStream_StreamNotFound_ThrowsException() {
        // Arrange
        String streamKey = "nonexistent-stream";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.startStream(streamKey, username));

        assertEquals("Stream not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void startStream_UnauthorizedUser_ThrowsException() {
        // Arrange
        String streamKey = "test-stream-key";
        String username = "unauthorized-user";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.startStream(streamKey, username));

        assertEquals("Unauthorized", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void stopStream_Success() {
        // Arrange
        String streamKey = "test-stream-key";
        String username = "streamer";
        testStream.setIsLive(true);
        testStream.setViewerCount(100);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        streamService.stopStream(streamKey, username);

        // Assert
        verify(streamRepository).save(argThat(stream ->
                !stream.getIsLive() &&
                        stream.getViewerCount() == 0 &&
                        stream.getEndedAt() != null
        ));
    }

    @Test
    void stopStream_StreamNotFound_ThrowsException() {
        // Arrange
        String streamKey = "nonexistent-stream";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.stopStream(streamKey, username));

        assertEquals("Stream not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void stopStream_UnauthorizedUser_ThrowsException() {
        // Arrange
        String streamKey = "test-stream-key";
        String username = "unauthorized-user";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.stopStream(streamKey, username));

        assertEquals("Unauthorized", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void getLiveStreams_Success() {
        // Arrange
        Stream liveStream1 = Stream.builder()
                .id(1L)
                .streamKey("stream1")
                .user(testUser)
                .isLive(true)
                .build();

        Stream liveStream2 = Stream.builder()
                .id(2L)
                .streamKey("stream2")
                .user(testUser)
                .isLive(true)
                .build();

        when(streamRepository.findByIsLiveTrue())
                .thenReturn(List.of(liveStream1, liveStream2));

        // Act
        List<StreamDTO> result = streamService.getLiveStreams();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(StreamDTO::getIsLive));
    }

    @Test
    void getLiveStreams_EmptyList() {
        // Arrange
        when(streamRepository.findByIsLiveTrue())
                .thenReturn(List.of());

        // Act
        List<StreamDTO> result = streamService.getLiveStreams();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getStreamByKey_Success() {
        // Arrange
        String streamKey = "test-stream-key";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));

        // Act
        StreamDTO result = streamService.getStreamByKey(streamKey);

        // Assert
        assertNotNull(result);
        assertEquals(testStream.getId(), result.getId());
        assertEquals(testStream.getStreamKey(), result.getStreamKey());
        assertEquals(testStream.getTitle(), result.getTitle());
        assertEquals(testStream.getUser().getId(), result.getUserId());
    }

    @Test
    void getStreamByKey_StreamNotFound_ThrowsException() {
        // Arrange
        String streamKey = "nonexistent-stream";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.getStreamByKey(streamKey));

        assertEquals("Stream not found", exception.getMessage());
    }

    @Test
    void updateViewerCount_Success() {
        // Arrange
        String streamKey = "test-stream-key";
        int newViewerCount = 150;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        streamService.updateViewerCount(streamKey, newViewerCount);

        // Assert
        verify(streamRepository).save(argThat(stream ->
                stream.getViewerCount() == newViewerCount
        ));
    }

    @Test
    void updateViewerCount_StreamNotFound_ThrowsException() {
        // Arrange
        String streamKey = "nonexistent-stream";
        int newViewerCount = 150;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.updateViewerCount(streamKey, newViewerCount));

        assertEquals("Stream not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void updateViewerCount_ZeroViewers() {
        // Arrange
        String streamKey = "test-stream-key";
        int newViewerCount = 0;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        streamService.updateViewerCount(streamKey, newViewerCount);

        // Assert
        verify(streamRepository).save(argThat(stream ->
                stream.getViewerCount() == 0
        ));
    }

    @Test
    void updateViewerCount_NegativeViewers() {
        // Arrange
        String streamKey = "test-stream-key";
        int newViewerCount = -10; // Edge case

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        streamService.updateViewerCount(streamKey, newViewerCount);

        // Assert
        verify(streamRepository).save(argThat(stream ->
                stream.getViewerCount() == newViewerCount
        ));
    }
}
