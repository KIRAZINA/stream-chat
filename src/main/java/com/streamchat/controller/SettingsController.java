package com.streamchat.controller;

import com.streamchat.model.entity.StreamSettings;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.StreamSettingsRepository;
import com.streamchat.service.StreamAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for stream chat settings.
 * Allows broadcasters to configure chat behavior.
 */
@RestController
@RequestMapping("/api/streams/{streamKey}/settings")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Settings", description = "Stream settings endpoints")
public class SettingsController {

    private final StreamRepository streamRepository;
    private final StreamSettingsRepository streamSettingsRepository;
    private final StreamAuthorizationService streamAuthorizationService;

    /**
     * Get stream settings.
     *
     * @param streamKey the stream key
     * @return stream settings
     */
    @GetMapping
    @Operation(summary = "Get stream settings")
    public ResponseEntity<StreamSettings> getSettings(
            @PathVariable String streamKey,
            Authentication authentication) {
        log.debug("Fetching settings for stream: {}", streamKey);

        if (authentication == null ||
                !streamAuthorizationService.canManageSettings(streamKey, authentication.getName())) {
            throw new com.streamchat.exception.UnauthorizedException("Insufficient permissions");
        }

        Long streamId = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"))
                .getId();
        StreamSettings settings = streamSettingsRepository.findByStreamId(streamId)
                .orElseThrow(() -> new RuntimeException("Settings not found"));

        return ResponseEntity.ok(settings);
    }

    /**
     * Update stream settings.
     *
     * @param streamKey the stream key
     * @param request settings update
     * @param authentication current broadcaster
     * @return updated settings
     */
    @PutMapping
    @PreAuthorize("@streamAuthorizationService.canManageSettings(#streamKey, authentication.name)")
    @Operation(summary = "Update stream settings")
    public ResponseEntity<StreamSettings> updateSettings(
            @PathVariable String streamKey,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        log.info("Updating settings: stream={}, by={}", streamKey, authentication.getName());

        if (!streamAuthorizationService.canManageSettings(streamKey, authentication.getName())) {
            throw new com.streamchat.exception.UnauthorizedException("Insufficient permissions");
        }

        Long streamId = streamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new RuntimeException("Stream not found"))
                .getId();
        StreamSettings settings = streamSettingsRepository.findByStreamId(streamId)
                .orElseThrow(() -> new RuntimeException("Settings not found"));

        // Update settings
        if (request.containsKey("slowModeEnabled")) {
            settings.setSlowModeEnabled((Boolean) request.get("slowModeEnabled"));
        }
        if (request.containsKey("slowModeSeconds")) {
            settings.setSlowModeSeconds((Integer) request.get("slowModeSeconds"));
        }
        if (request.containsKey("followersOnlyMode")) {
            settings.setFollowersOnlyMode((Boolean) request.get("followersOnlyMode"));
        }
        if (request.containsKey("subscribersOnlyMode")) {
            settings.setSubscribersOnlyMode((Boolean) request.get("subscribersOnlyMode"));
        }
        if (request.containsKey("emoteOnlyMode")) {
            settings.setEmoteOnlyMode((Boolean) request.get("emoteOnlyMode"));
        }
        if (request.containsKey("maxMessageLength")) {
            settings.setMaxMessageLength((Integer) request.get("maxMessageLength"));
        }
        if (request.containsKey("profanityFilterEnabled")) {
            settings.setProfanityFilterEnabled((Boolean) request.get("profanityFilterEnabled"));
        }
        if (request.containsKey("linkProtectionEnabled")) {
            settings.setLinkProtectionEnabled((Boolean) request.get("linkProtectionEnabled"));
        }

        StreamSettings updated = streamSettingsRepository.save(settings);
        return ResponseEntity.ok(updated);
    }
}
