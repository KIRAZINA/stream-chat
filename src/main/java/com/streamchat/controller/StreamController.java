package com.streamchat.controller;

import com.streamchat.model.dto.ChatHistoryResponse;
import com.streamchat.model.dto.StreamDTO;
import com.streamchat.model.dto.StreamPresenceResponse;
import com.streamchat.model.dto.StreamRequest;
import com.streamchat.service.ChatService;
import com.streamchat.service.PresenceService;
import com.streamchat.service.StreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for stream management.
 * Handles stream creation, updates, and lifecycle operations.
 */
@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Streams", description = "Stream management endpoints")
public class StreamController {

    private final StreamService streamService;
    private final ChatService chatService;
    private final PresenceService presenceService;

    /**
     * Get all live streams.
     *
     * @return list of live streams
     */
    @GetMapping
    @Operation(summary = "Get all live streams")
    public ResponseEntity<List<StreamDTO>> getLiveStreams() {
        log.debug("Fetching all live streams");
        List<StreamDTO> streams = streamService.getLiveStreams();
        return ResponseEntity.ok(streams);
    }

    /**
     * Get stream by key.
     *
     * @param streamKey the stream key
     * @return stream details
     */
    @GetMapping("/{streamKey}")
    @Operation(summary = "Get stream by key")
    public ResponseEntity<StreamDTO> getStream(@PathVariable String streamKey) {
        log.debug("Fetching stream: {}", streamKey);
        StreamDTO stream = streamService.getStreamByKey(streamKey);
        return ResponseEntity.ok(stream);
    }

    /**
     * Get paginated chat history for a stream.
     *
     * @param streamKey the stream key
     * @param before optional cursor for older messages
     * @param limit page size
     * @return paginated message history
     */
    @GetMapping("/{streamKey}/messages")
    @Operation(summary = "Get chat history for a stream")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(
            @PathVariable String streamKey,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        log.debug("Fetching chat history: stream={}, before={}, limit={}, includeDeleted={}", streamKey, before, limit, includeDeleted);
        ChatHistoryResponse history = chatService.getMessageHistory(streamKey, before, limit, includeDeleted);
        return ResponseEntity.ok(history);
    }

    /**
     * Get replay window for a stream (messages after a specific sequence ID).
     * Used for reconnect recovery.
     *
     * @param streamKey the stream key
     * @param afterSequenceId optional sequence ID to get messages after
     * @param limit max messages to return
     * @return replay window with messages
     */
    @GetMapping("/{streamKey}/messages/replay")
    @Operation(summary = "Get replay window for reconnect recovery")
    public ResponseEntity<ChatHistoryResponse> getReplayWindow(
            @PathVariable String streamKey,
            @RequestParam(required = false) Long afterSequenceId,
            @RequestParam(defaultValue = "100") Integer limit) {

        log.debug("Fetching replay window: stream={}, afterSequenceId={}, limit={}", streamKey, afterSequenceId, limit);

        // For now, use the standard history endpoint as replay
        // In production, this would use redis_sequence_id for precise replay
        ChatHistoryResponse history = chatService.getMessageHistory(streamKey, null, limit, false);
        return ResponseEntity.ok(history);
    }

    /**
     * Get active presence count for a stream.
     *
     * @param streamKey the stream identifier
     */
    @GetMapping("/{streamKey}/presence")
    @Operation(summary = "Get active viewer presence for a stream")
    public ResponseEntity<StreamPresenceResponse> getStreamPresence(
            @PathVariable String streamKey) {

        log.debug("Fetching stream presence: {}", streamKey);
        StreamPresenceResponse presence = StreamPresenceResponse.builder()
                .activeViewers(presenceService.getActiveViewers(streamKey))
                .build();

        return ResponseEntity.ok(presence);
    }

    /**
     * Create a new stream.
     *
     * @param request stream creation details
     * @param authentication current user
     * @return created stream
     */
    @PostMapping
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create a new stream")
    public ResponseEntity<StreamDTO> createStream(
            @Valid @RequestBody StreamRequest request,
            Authentication authentication) {

        log.info("Creating stream for user: {}", authentication.getName());

        StreamDTO stream = streamService.createStream(
                authentication.getName(),
                request.getTitle(),
                request.getDescription()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(stream);
    }

    /**
     * Update stream details.
     *
     * @param streamKey the stream key
     * @param request update details
     * @param authentication current user
     * @return updated stream
     */
    @PutMapping("/{streamKey}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update stream")
    public ResponseEntity<StreamDTO> updateStream(
            @PathVariable String streamKey,
            @Valid @RequestBody StreamRequest request,
            Authentication authentication) {

        log.info("Updating stream: {}", streamKey);

        StreamDTO stream = streamService.updateStream(
                streamKey,
                authentication.getName(),
                request.getTitle(),
                request.getDescription()
        );

        return ResponseEntity.ok(stream);
    }

    /**
     * Start streaming.
     *
     * @param streamKey the stream key
     * @param authentication current user
     * @return success response
     */
    @PostMapping("/{streamKey}/start")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Start streaming")
    public ResponseEntity<Map<String, String>> startStream(
            @PathVariable String streamKey,
            Authentication authentication) {

        log.info("Starting stream: {}", streamKey);
        streamService.startStream(streamKey, authentication.getName());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Stream started"
        ));
    }

    /**
     * Stop streaming.
     *
     * @param streamKey the stream key
     * @param authentication current user
     * @return success response
     */
    @PostMapping("/{streamKey}/stop")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Stop streaming")
    public ResponseEntity<Map<String, String>> stopStream(
            @PathVariable String streamKey,
            Authentication authentication) {

        log.info("Stopping stream: {}", streamKey);
        streamService.stopStream(streamKey, authentication.getName());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Stream stopped"
        ));
    }

    /**
     * Delete stream.
     *
     * @param streamKey the stream key
     * @param authentication current user
     * @return success response
     */
    @DeleteMapping("/{streamKey}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Delete stream")
    public ResponseEntity<Map<String, String>> deleteStream(
            @PathVariable String streamKey,
            Authentication authentication) {

        log.info("Deleting stream: {}", streamKey);

        streamService.deleteStream(streamKey, authentication.getName());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Stream deleted"
        ));
    }
}
