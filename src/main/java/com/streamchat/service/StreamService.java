package com.streamchat.service;

import com.streamchat.model.dto.StreamDTO;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.StreamSettings;
import com.streamchat.model.entity.User;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing streams.
 * Handles stream creation, updates, and lifecycle management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamService {

    private final StreamRepository streamRepository;
    private final StreamSettingsRepository streamSettingsRepository;
    private final UserRepository userRepository;

    /**
     * Create a new stream for a user.
     *
     * @param username    the username
     * @param title       the stream title
     * @param description the stream description
     * @return created stream DTO
     */
    @Transactional
    public StreamDTO createStream(String username, String title, String description) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate unique stream key
        String streamKey = generateStreamKey();

        // Create stream
        Stream stream = Stream.builder()
                .streamKey(streamKey)
                .user(user)
                .title(title)
                .description(description)
                .isLive(false)
                .build();

        Stream saved = streamRepository.save(stream);

        // Create default settings
        StreamSettings settings = StreamSettings.builder()
                .stream(saved)
                .build();
        streamSettingsRepository.save(settings);

        log.info("Stream created: streamKey={}, userId={}", streamKey, user.getId());

        return convertToDTO(saved);
    }

    /**
     * Start a stream.
     *
     * @param streamKey the stream key
     * @param username  the username
     */
    @Transactional
    public void startStream(String streamKey, String username) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        // Verify ownership
        if (!stream.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized");
        }

        stream.setIsLive(true);
        stream.setStartedAt(LocalDateTime.now());
        streamRepository.save(stream);

        log.info("Stream started: streamKey={}", streamKey);
    }

    /**
     * Stop a stream.
     *
     * @param streamKey the stream key
     * @param username  the username
     */
    @Transactional
    public void stopStream(String streamKey, String username) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        // Verify ownership
        if (!stream.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized");
        }

        stream.setIsLive(false);
        stream.setEndedAt(LocalDateTime.now());
        stream.setViewerCount(0);
        streamRepository.save(stream);

        log.info("Stream stopped: streamKey={}", streamKey);
    }

    /**
     * Update stream details.
     *
     * @param streamKey   the stream key
     * @param username    the username
     * @param title       the new title
     * @param description the new description
     * @return updated stream DTO
     */
    @Transactional
    public StreamDTO updateStream(String streamKey, String username, String title, String description) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        // Verify ownership
        if (!stream.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized");
        }

        if (title != null) {
            stream.setTitle(title);
        }
        if (description != null) {
            stream.setDescription(description);
        }

        Stream saved = streamRepository.save(stream);
        log.info("Stream updated: streamKey={}", streamKey);

        return convertToDTO(saved);
    }

    /**
     * Delete a stream.
     *
     * @param streamKey the stream key
     * @param username  the username
     */
    @Transactional
    public void deleteStream(String streamKey, String username) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        // Verify ownership
        if (!stream.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized");
        }

        streamRepository.delete(stream);
        log.info("Stream deleted: streamKey={}", streamKey);
    }

    /**
     * Get all live streams.
     *
     * @return list of live streams
     */
    public List<StreamDTO> getLiveStreams() {
        return streamRepository.findByIsLiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get stream by key.
     *
     * @param streamKey the stream key
     * @return stream DTO
     */
    public StreamDTO getStreamByKey(String streamKey) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));
        return convertToDTO(stream);
    }

    /**
     * Update viewer count.
     *
     * @param streamKey the stream key
     * @param count     the new viewer count
     */
    @Transactional
    public void updateViewerCount(String streamKey, int count) {
        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"));

        stream.setViewerCount(count);
        streamRepository.save(stream);
    }

    /**
     * Generate unique stream key.
     */
    private String generateStreamKey() {
        String key;
        do {
            key = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } while (streamRepository.existsByStreamKey(key));
        return key;
    }

    /**
     * Convert entity to DTO.
     */
    private StreamDTO convertToDTO(Stream stream) {
        return StreamDTO.builder()
                .id(stream.getId())
                .streamKey(stream.getStreamKey())
                .userId(stream.getUser().getId())
                .username(stream.getUser().getUsername())
                .title(stream.getTitle())
                .description(stream.getDescription())
                .isLive(stream.getIsLive())
                .viewerCount(stream.getViewerCount())
                .startedAt(stream.getStartedAt())
                .createdAt(stream.getCreatedAt())
                .build();
    }
}