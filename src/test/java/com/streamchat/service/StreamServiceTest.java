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
        StreamDTO result = streamService.createStream(username, title, description);
        assertNotNull(result);
        assertNotNull(result.getStreamKey());
        assertEquals(title, result.getTitle());
        assertEquals(description, result.getDescription());
        assertEquals("OFFLINE", result.getStatus());
        assertEquals(testUser.getId(), result.getOwnerId());
        verify(streamRepository).save(any(Stream.class));
        verify(streamSettingsRepository).save(any(StreamSettings.class));
    }

    @Test
    void createStream_UserNotFound_ThrowsException() {
        String username = "nonexistent";
        String title = "My Stream";
        String description = "Stream Description";

        when(userRepository.findByUsername(username))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.createStream(username, title, description));

        assertEquals("User not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void createStream_GeneratesUniqueStreamKey() {
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
        StreamDTO result = streamService.createStream(username, title, description);
        assertNotNull(result.getStreamKey());
        assertEquals(16, result.getStreamKey().length()); // UUID substring length
        verify(streamRepository).save(any(Stream.class));
    }

    @Test
    void startStream_Success() {
        String streamKey = "test-stream-key";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        streamService.startStream(streamKey, username);
        verify(streamRepository).save(argThat(stream ->
                stream.getIsLive() && stream.getStartedAt() != null
        ));
    }

    @Test
    void startStream_StreamNotFound_ThrowsException() {
        String streamKey = "nonexistent-stream";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.startStream(streamKey, username));

        assertEquals("Stream not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void startStream_UnauthorizedUser_ThrowsException() {
        String streamKey = "test-stream-key";
        String username = "unauthorized-user";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.startStream(streamKey, username));

        assertEquals("Unauthorized", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void updateStream_Success() {
        String streamKey = "test-stream-key";
        String username = "streamer";
        String newTitle = "Updated Title";
        String newDescription = "Updated Description";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        StreamDTO result = streamService.updateStream(streamKey, username, newTitle, newDescription);
        assertEquals(newTitle, result.getTitle());
        assertEquals(newDescription, result.getDescription());
        verify(streamRepository).save(any(Stream.class));
    }

    @Test
    void updateStream_StreamNotFound_ThrowsException() {
        String streamKey = "nonexistent-stream";
        String username = "streamer";
        String newTitle = "Updated Title";
        String newDescription = "Updated Description";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.updateStream(streamKey, username, newTitle, newDescription));

        assertEquals("Stream not found", exception.getMessage());
    }

    @Test
    void updateStream_Unauthorized_ThrowsException() {
        String streamKey = "test-stream-key";
        String username = "other-user";
        String newTitle = "Updated Title";
        String newDescription = "Updated Description";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.updateStream(streamKey, username, newTitle, newDescription));

        assertEquals("Unauthorized", exception.getMessage());
    }

    @Test
    void deleteStream_Success() {
        String streamKey = "test-stream-key";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        streamService.deleteStream(streamKey, username);
        verify(streamRepository).delete(testStream);
    }

    @Test
    void deleteStream_StreamNotFound_ThrowsException() {
        String streamKey = "nonexistent-stream";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.deleteStream(streamKey, username));

        assertEquals("Stream not found", exception.getMessage());
    }

    @Test
    void deleteStream_Unauthorized_ThrowsException() {
        String streamKey = "test-stream-key";
        String username = "other-user";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.deleteStream(streamKey, username));

        assertEquals("Unauthorized", exception.getMessage());
    }

    @Test
    void stopStream_Success() {
        String streamKey = "test-stream-key";
        String username = "streamer";
        testStream.setIsLive(true);
        testStream.setViewerCount(100);

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        streamService.stopStream(streamKey, username);
        verify(streamRepository).save(argThat(stream ->
                !stream.getIsLive() &&
                        stream.getViewerCount() == 0 &&
                        stream.getEndedAt() != null
        ));
    }

    @Test
    void stopStream_StreamNotFound_ThrowsException() {
        String streamKey = "nonexistent-stream";
        String username = "streamer";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.stopStream(streamKey, username));

        assertEquals("Stream not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void stopStream_UnauthorizedUser_ThrowsException() {
        String streamKey = "test-stream-key";
        String username = "unauthorized-user";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.stopStream(streamKey, username));

        assertEquals("Unauthorized", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void getLiveStreams_Success() {
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
        List<StreamDTO> result = streamService.getLiveStreams();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(dto -> "LIVE".equals(dto.getStatus())));
    }

    @Test
    void getLiveStreams_EmptyList() {
        when(streamRepository.findByIsLiveTrue())
                .thenReturn(List.of());
        List<StreamDTO> result = streamService.getLiveStreams();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getStreamByKey_Success() {
        String streamKey = "test-stream-key";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        StreamDTO result = streamService.getStreamByKey(streamKey);
        assertNotNull(result);
        assertEquals(testStream.getId(), result.getId());
        assertEquals(testStream.getStreamKey(), result.getStreamKey());
        assertEquals(testStream.getTitle(), result.getTitle());
        assertEquals(testStream.getUser().getId(), result.getOwnerId());
    }

    @Test
    void getStreamByKey_StreamNotFound_ThrowsException() {
        String streamKey = "nonexistent-stream";

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.getStreamByKey(streamKey));

        assertEquals("Stream not found", exception.getMessage());
    }

    @Test
    void updateViewerCount_Success() {
        String streamKey = "test-stream-key";
        int newViewerCount = 150;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        streamService.updateViewerCount(streamKey, newViewerCount);
        verify(streamRepository).save(argThat(stream ->
                stream.getViewerCount() == newViewerCount
        ));
    }

    @Test
    void updateViewerCount_StreamNotFound_ThrowsException() {
        String streamKey = "nonexistent-stream";
        int newViewerCount = 150;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                streamService.updateViewerCount(streamKey, newViewerCount));

        assertEquals("Stream not found", exception.getMessage());
        verify(streamRepository, never()).save(any());
    }

    @Test
    void updateViewerCount_ZeroViewers() {
        String streamKey = "test-stream-key";
        int newViewerCount = 0;

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        streamService.updateViewerCount(streamKey, newViewerCount);
        verify(streamRepository).save(argThat(stream ->
                stream.getViewerCount() == 0
        ));
    }

    @Test
    void updateViewerCount_NegativeViewers() {
        String streamKey = "test-stream-key";
        int newViewerCount = -10; // Edge case

        when(streamRepository.findByStreamKey(streamKey))
                .thenReturn(Optional.of(testStream));
        when(streamRepository.save(any(Stream.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        streamService.updateViewerCount(streamKey, newViewerCount);
        verify(streamRepository).save(argThat(stream ->
                stream.getViewerCount() == newViewerCount
        ));
    }
}
