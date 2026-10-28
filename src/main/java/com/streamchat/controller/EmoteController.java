package com.streamchat.controller;

import com.streamchat.model.dto.EmoteDTO;
import com.streamchat.model.entity.Stream;
import com.streamchat.repository.StreamRepository;
import com.streamchat.service.EmoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmoteController {

    private final EmoteService emoteService;
    private final StreamRepository streamRepository;

    @GetMapping("/emotes/global")
    public ResponseEntity<List<EmoteDTO>> getGlobalEmotes(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        List<EmoteDTO> emotes = emoteService.toDto(emoteService.getGlobalEmotes());
        String etag = Integer.toHexString(emotes.hashCode());
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic().immutable())
                .body(emotes);
    }

    @GetMapping("/streams/{streamKey}/emotes")
    public ResponseEntity<List<EmoteDTO>> getStreamEmotes(
            @PathVariable String streamKey,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        Stream stream = streamRepository.findByStreamKey(streamKey).orElse(null);
        Long streamId = stream != null ? stream.getId() : null;
        List<EmoteDTO> emotes = emoteService.toDto(emoteService.getStreamEmotes(streamId));
        String etag = Integer.toHexString(emotes.hashCode());
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic().immutable())
                .body(emotes);
    }
}
