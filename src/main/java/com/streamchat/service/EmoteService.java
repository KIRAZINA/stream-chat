package com.streamchat.service;

import com.streamchat.model.entity.Emote;
import com.streamchat.repository.EmoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing emotes.
 * Handles emote parsing and replacement in messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmoteService {

    private final EmoteRepository emoteRepository;

    /**
     * Parse message and replace emote codes with HTML img tags.
     *
     * @param streamId the stream ID
     * @param message the message content
     * @return parsed message with emote HTML
     */
    public String parseEmotes(Long streamId, String message) {
        List<Emote> emotes = emoteRepository.findByStreamIdOrGlobal(streamId);

        // Build emote map for quick lookup
        Map<String, String> emoteMap = emotes.stream()
                .collect(Collectors.toMap(
                        Emote::getCode,
                        e -> String.format("<img src='%s' alt='%s' class='emote' />",
                                e.getImageUrl(), e.getCode()),
                        (existing, replacement) -> existing
                ));

        String parsed = message;
        for (Map.Entry<String, String> entry : emoteMap.entrySet()) {
            // Replace whole word matches only
            parsed = parsed.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
        }

        return parsed;
    }

    /**
     * Get all emotes for a stream.
     *
     * @param streamId the stream ID
     * @return list of emotes
     */
    public List<Emote> getStreamEmotes(Long streamId) {
        return emoteRepository.findByStreamIdOrGlobal(streamId);
    }
}