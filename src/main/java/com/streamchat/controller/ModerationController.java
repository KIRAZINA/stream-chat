package com.streamchat.controller;

import com.streamchat.model.dto.ModerationActionDTO;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.ModerationActionType;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.ModerationLogRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for moderation operations.
 * Handles user timeouts, bans, and message deletion.
 */
@RestController
@RequestMapping("/api/streams/{streamKey}/moderate")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Moderation", description = "Chat moderation endpoints")
public class ModerationController {

    private final ModerationService moderationService;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final UserStreamRoleRepository userStreamRoleRepository;

    /**
     * Timeout a user.
     *
     * @param streamKey the stream key
     * @param request timeout details
     * @param authentication current moderator
     * @return success response
     */
    @PostMapping("/timeout")
    @PreAuthorize("hasAnyRole('MODERATOR', 'BROADCASTER')")
    @Operation(summary = "Timeout a user")
    public ResponseEntity<Map<String, String>> timeoutUser(
            @PathVariable String streamKey,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        log.info("Timeout request: stream={}, moderator={}",
                streamKey, authentication.getName());

        String targetUsername = (String) request.get("username");
        Integer duration = (Integer) request.get("durationSeconds");
        String reason = (String) request.get("reason");

        // TODO: Get actual IDs from repositories
        Long streamId = 1L; // Placeholder
        Long userId = 1L; // Placeholder
        Long moderatorId = 1L; // Placeholder

        moderationService.timeoutUser(streamId, userId, moderatorId, duration, reason);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "User timed out for " + duration + " seconds"
        ));
    }

    /**
     * Ban a user.
     *
     * @param streamKey the stream key
     * @param request ban details
     * @param authentication current moderator
     * @return success response
     */
    @PostMapping("/ban")
    @PreAuthorize("hasAnyRole('MODERATOR', 'BROADCASTER')")
    @Operation(summary = "Ban a user")
    public ResponseEntity<Map<String, String>> banUser(
            @PathVariable String streamKey,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        log.info("Ban request: stream={}, moderator={}",
                streamKey, authentication.getName());

        String targetUsername = (String) request.get("username");
        String reason = (String) request.get("reason");
        Boolean permanent = (Boolean) request.getOrDefault("permanent", true);
        Integer duration = (Integer) request.get("durationSeconds");

        // TODO: Get actual IDs from repositories
        Long streamId = 1L; // Placeholder
        Long userId = 1L; // Placeholder
        Long moderatorId = 1L; // Placeholder

        moderationService.banUser(streamId, userId, moderatorId, permanent, duration, reason);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "User banned"
        ));
    }

    /**
     * Unban a user.
     *
     * @param streamKey the stream key
     * @param userId the user ID to unban
     * @param authentication current moderator
     * @return success response
     */
    @DeleteMapping("/ban/{userId}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'BROADCASTER')")
    @Operation(summary = "Unban a user")
    public ResponseEntity<Map<String, String>> unbanUser(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Unban request: stream={}, userId={}, moderator={}",
                streamKey, userId, authentication.getName());

        // TODO: Get actual IDs from repositories
        Long streamId = 1L; // Placeholder
        Long moderatorId = 1L; // Placeholder

        moderationService.unbanUser(streamId, userId, moderatorId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "User unbanned"
        ));
    }

    /**
     * Delete a message.
     *
     * @param streamKey the stream key
     * @param messageId the message ID
     * @param authentication current moderator
     * @return success response
     */
    @DeleteMapping("/messages/{messageId}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'BROADCASTER')")
    @Operation(summary = "Delete a message")
    public ResponseEntity<Map<String, String>> deleteMessage(
            @PathVariable String streamKey,
            @PathVariable Long messageId,
            Authentication authentication) {

        log.info("Delete message request: stream={}, messageId={}, moderator={}",
                streamKey, messageId, authentication.getName());

        chatService.deleteMessage(messageId, authentication.getName());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Message deleted"
        ));
    }

    /**
     * Get moderation logs for a stream.
     *
     * @param streamKey the stream key
     * @return list of moderation logs
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('MODERATOR', 'BROADCASTER')")
    @Operation(summary = "Get moderation logs")
    public ResponseEntity<List<ModerationLog>> getModerationLogs(
            @PathVariable String streamKey) {

        log.debug("Fetching moderation logs: stream={}", streamKey);

        // TODO: Get actual stream ID
        Long streamId = 1L; // Placeholder
        List<ModerationLog> logs = moderationLogRepository
                .findByStreamIdOrderByCreatedAtDesc(streamId);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get list of moderators.
     *
     * @param streamKey the stream key
     * @return list of moderators
     */
    @GetMapping("/moderators")
    @Operation(summary = "Get list of moderators")
    public ResponseEntity<List<UserStreamRole>> getModerators(
            @PathVariable String streamKey) {

        log.debug("Fetching moderators: stream={}", streamKey);

        // TODO: Get actual stream ID
        Long streamId = 1L; // Placeholder
        List<UserStreamRole> moderators = userStreamRoleRepository.findModerators(streamId);

        return ResponseEntity.ok(moderators);
    }

    /**
     * Add a moderator.
     *
     * @param streamKey the stream key
     * @param request moderator details
     * @param authentication current broadcaster
     * @return success response
     */
    @PostMapping("/moderators")
    @PreAuthorize("hasRole('BROADCASTER')")
    @Operation(summary = "Add a moderator")
    public ResponseEntity<Map<String, String>> addModerator(
            @PathVariable String streamKey,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String username = request.get("username");
        log.info("Adding moderator: stream={}, user={}, by={}",
                streamKey, username, authentication.getName());

        // TODO: Implement add moderator logic

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Moderator added"
        ));
    }

    /**
     * Remove a moderator.
     *
     * @param streamKey the stream key
     * @param userId the moderator user ID
     * @param authentication current broadcaster
     * @return success response
     */
    @DeleteMapping("/moderators/{userId}")
    @PreAuthorize("hasRole('BROADCASTER')")
    @Operation(summary = "Remove a moderator")
    public ResponseEntity<Map<String, String>> removeModerator(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Removing moderator: stream={}, userId={}, by={}",
                streamKey, userId, authentication.getName());

        // TODO: Get actual stream ID
        Long streamId = 1L; // Placeholder

        userStreamRoleRepository.deleteByUserIdAndStreamIdAndRole(
                userId, streamId, Role.ROLE_MODERATOR);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Moderator removed"
        ));
    }
}