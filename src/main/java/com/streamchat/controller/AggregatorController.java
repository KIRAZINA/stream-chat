package com.streamchat.controller;

import com.streamchat.aggregator.ExternalChatMessagePayload;
import com.streamchat.aggregator.ExternalPlatform;
import com.streamchat.aggregator.MultiPlatformChatAggregator;
import com.streamchat.model.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
public class AggregatorController {

    private final MultiPlatformChatAggregator aggregator;

    @PostMapping("/{streamKey}/aggregator/webhook/{platform}")
    public ResponseEntity<?> webhook(@PathVariable String streamKey,
                                     @PathVariable String platform,
                                     @RequestBody Map<String, Object> body) {
        ExternalPlatform p;
        try {
            p = ExternalPlatform.valueOf(platform.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown platform");
        }
        ExternalChatMessagePayload payload = ExternalChatMessagePayload.builder()
                .platform(p)
                .externalMessageId(String.valueOf(body.getOrDefault("externalMessageId", "")))
                .externalChannelId(String.valueOf(body.getOrDefault("externalChannelId", "")))
                .externalSenderId(String.valueOf(body.getOrDefault("externalSenderId", "")))
                .externalSenderUsername(String.valueOf(body.getOrDefault("externalSenderUsername", "")))
                .externalSenderDisplayName(String.valueOf(body.getOrDefault("externalSenderDisplayName", "")))
                .externalAvatarUrl(String.valueOf(body.getOrDefault("externalAvatarUrl", "")))
                .content(String.valueOf(body.getOrDefault("content", "")))
                .timestamp(LocalDateTime.now())
                .platformMetadata((Map<String, String>) body.get("platformMetadata"))
                .build();
        ChatMessageDTO dto = aggregator.ingestExternalMessage(payload, streamKey);
        if (dto == null) {
            return ResponseEntity.status(204).build();
        }
        return ResponseEntity.ok(dto);
    }
}
