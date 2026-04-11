package com.streamchat.controller;

import com.streamchat.exception.ResourceNotFoundException;
import com.streamchat.model.dto.AddModeratorRequest;
import com.streamchat.model.dto.BanRequest;
import com.streamchat.model.dto.TimeoutRequest;
import com.streamchat.model.entity.ModerationLog;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.model.entity.UserStreamRole;
import com.streamchat.model.enums.Role;
import com.streamchat.repository.ModerationLogRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import com.streamchat.service.AutoModService;
import com.streamchat.service.ChatService;
import com.streamchat.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final StreamRepository streamRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final UserStreamRoleRepository userStreamRoleRepository;
    private final AutoModService autoModService;

    /**
     * Timeout a user.
     *
     * @param streamKey the stream key
     * @param request timeout details
     * @param authentication current moderator
     * @return success response
     */
    @PostMapping("/timeout")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Timeout a user")
    public ResponseEntity<Map<String, String>> timeoutUser(
            @PathVariable String streamKey,
            @Valid @RequestBody TimeoutRequest request,
            Authentication authentication) {

        log.info("Timeout request: stream={}, moderator={}",
                streamKey, authentication.getName());

        String targetUsername = request.getUsername();
        Integer duration = request.getDurationSeconds();
        String reason = request.getReason();

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUsername));

        User moderator = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Moderator not found"));

        moderationService.timeoutUser(stream.getId(), targetUser.getId(), moderator.getId(), duration, reason);

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
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Ban a user")
    public ResponseEntity<Map<String, String>> banUser(
            @PathVariable String streamKey,
            @Valid @RequestBody BanRequest request,
            Authentication authentication) {

        log.info("Ban request: stream={}, moderator={}",
                streamKey, authentication.getName());

        String targetUsername = request.getUsername();
        String reason = request.getReason();
        Boolean permanent = request.getPermanent();
        if (permanent == null) {
            permanent = true;
        }
        Integer duration = request.getDurationSeconds();
        if (!Boolean.TRUE.equals(permanent) && (duration == null || duration <= 0)) {
            throw new IllegalArgumentException("durationSeconds must be positive for temporary bans");
        }

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUsername));

        User moderator = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Moderator not found"));

        moderationService.banUser(stream.getId(), targetUser.getId(), moderator.getId(), permanent, duration, reason);

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
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Unban a user")
    public ResponseEntity<Map<String, String>> unbanUser(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Unban request: stream={}, userId={}, moderator={}",
                streamKey, userId, authentication.getName());

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        User moderator = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Moderator not found"));

        moderationService.unbanUser(stream.getId(), userId, moderator.getId());

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
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Delete a message")
    public ResponseEntity<Map<String, String>> deleteMessage(
            @PathVariable String streamKey,
            @PathVariable Long messageId,
            Authentication authentication) {

        log.info("Delete message request: stream={}, messageId={}, moderator={}",
                streamKey, messageId, authentication.getName());

        // Verify stream existence even though deleteMessage handles logic
        if (!streamRepository.existsByStreamKey(streamKey)) {
             throw new ResourceNotFoundException("Stream not found");
        }

        chatService.deleteMessage(messageId, authentication.getName());
        messagingTemplate.convertAndSend(
                "/topic/stream/" + streamKey + "/moderation",
                Map.of(
                        "action", "DELETE_MESSAGE",
                        "messageId", messageId,
                        "deletedBy", authentication.getName()
                )
        );

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Message deleted"
        ));
    }

    /**
     * Delete all messages by a user in the stream.
     *
     * @param streamKey the stream identifier
     * @param userId the user ID whose messages should be deleted
     * @param authentication current moderator
     * @return success response
     */
    @DeleteMapping("/messages/user/{userId}")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Delete all messages by a user")
    public ResponseEntity<Map<String, Object>> deleteUserMessages(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Bulk delete messages request: stream={}, userId={}, moderator={}",
                streamKey, userId, authentication.getName());

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        int deletedCount = chatService.deleteMessagesByUser(stream.getId(), userId, authentication.getName());
        messagingTemplate.convertAndSend(
                "/topic/stream/" + streamKey + "/moderation",
                Map.of(
                        "action", "DELETE_USER_MESSAGES",
                        "targetUserId", userId,
                        "deletedBy", authentication.getName(),
                        "deletedCount", deletedCount
                )
        );

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "deletedCount", deletedCount
        ));
    }

    /**
     * Get moderation logs for a stream.
     *
     * @param streamKey the stream key
     * @return list of moderation logs
     */
    @GetMapping("/logs")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Get moderation logs")
    public ResponseEntity<List<ModerationLog>> getModerationLogs(
            @PathVariable String streamKey) {

        log.debug("Fetching moderation logs: stream={}", streamKey);

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        List<ModerationLog> logs = moderationLogRepository
                .findByStreamIdOrderByCreatedAtDesc(stream.getId());

        return ResponseEntity.ok(logs);
    }

    /**
     * Get list of moderators.
     *
     * @param streamKey the stream key
     * @return list of moderators
     */
    @GetMapping("/moderators")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Get list of moderators")
    public ResponseEntity<List<UserStreamRole>> getModerators(
            @PathVariable String streamKey) {

        log.debug("Fetching moderators: stream={}", streamKey);

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        List<UserStreamRole> moderators = userStreamRoleRepository.findModerators(stream.getId());

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
    @PreAuthorize("@streamAuthorizationService.canManageSettings(#streamKey, authentication.name)")
    @Operation(summary = "Add a moderator")
    public ResponseEntity<Map<String, String>> addModerator(
            @PathVariable String streamKey,
            @Valid @RequestBody AddModeratorRequest request,
            Authentication authentication) {

        String username = request.getUsername();
        log.info("Adding moderator: stream={}, user={}, by={}",
                streamKey, username, authentication.getName());

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Create new role
        UserStreamRole role = UserStreamRole.builder()
                .user(user)
                .stream(stream)
                .role(Role.ROLE_MODERATOR)
                .grantedAt(java.time.LocalDateTime.now())
                .grantedBy(userRepository.findByUsername(authentication.getName()).orElseThrow())
                .build();

        userStreamRoleRepository.save(role);

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
    @PreAuthorize("@streamAuthorizationService.canManageSettings(#streamKey, authentication.name)")
    @Operation(summary = "Remove a moderator")
    public ResponseEntity<Map<String, String>> removeModerator(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Removing moderator: stream={}, userId={}, by={}",
                streamKey, userId, authentication.getName());

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        userStreamRoleRepository.deleteByUserIdAndStreamIdAndRole(
                userId, stream.getId(), Role.ROLE_MODERATOR);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Moderator removed"
        ));
    }

    /**
     * Enable shadow ban for a user.
     * Shadow-banned users can send messages, but only they see them.
     */
    @PostMapping("/shadow-ban/{userId}")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Enable shadow ban for a user")
    public ResponseEntity<Map<String, String>> enableShadowBan(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Shadow ban request: stream={}, userId={}, moderator={}",
                streamKey, userId, authentication.getName());

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        autoModService.enableShadowBan(stream.getId(), userId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Shadow ban enabled for user"
        ));
    }

    /**
     * Disable shadow ban for a user.
     */
    @DeleteMapping("/shadow-ban/{userId}")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Disable shadow ban for a user")
    public ResponseEntity<Map<String, String>> disableShadowBan(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.info("Shadow ban removal request: stream={}, userId={}, moderator={}",
                streamKey, userId, authentication.getName());

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        autoModService.disableShadowBan(stream.getId(), userId);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Shadow ban disabled for user"
        ));
    }

    /**
     * Get trust score for a user.
     */
    @GetMapping("/trust-score/{userId}")
    @PreAuthorize("@streamAuthorizationService.canModerate(#streamKey, authentication.name)")
    @Operation(summary = "Get AutoMod trust score for a user")
    public ResponseEntity<Map<String, Object>> getTrustScore(
            @PathVariable String streamKey,
            @PathVariable Long userId,
            Authentication authentication) {

        log.debug("Trust score request: stream={}, userId={}", streamKey, userId);

        Stream stream = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found"));

        double score = autoModService.getTrustScore(stream.getId(), userId);
        boolean isShadowBanned = autoModService.isShadowBanned(stream.getId(), userId);

        return ResponseEntity.ok(Map.of(
                "trustScore", score,
                "shadowBanned", isShadowBanned
        ));
    }
}
