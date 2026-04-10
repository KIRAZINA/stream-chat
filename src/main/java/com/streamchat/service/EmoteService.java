package com.streamchat.service;

import com.streamchat.model.entity.Emote;
import com.streamchat.repository.EmoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.streamchat.model.dto.MessageFragmentDTO;
import com.streamchat.model.enums.MessageFragmentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Build structured message fragments for safe client rendering.
     * Emote tokens like :smile: are preserved as EMOTE fragments.
     *
     * @param streamId the stream ID
     * @param message the raw chat message
     * @return list of message fragments
     */
    public List<MessageFragmentDTO> buildMessageFragments(Long streamId, String message) {
        if (message == null || message.isEmpty()) {
            return List.of();
        }

        List<Emote> emotes = emoteRepository.findByStreamIdOrGlobal(streamId);
        Map<String, Emote> emoteMap = emotes.stream()
                .collect(Collectors.toMap(
                        Emote::getCode,
                        e -> e,
                        (existing, replacement) -> existing
                ));

        Pattern pattern = Pattern.compile("(:[A-Za-z0-9_]+:)");
        Matcher matcher = pattern.matcher(message);
        int lastIndex = 0;
        List<MessageFragmentDTO> fragments = new ArrayList<>();

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                fragments.add(MessageFragmentDTO.builder()
                        .type(MessageFragmentType.TEXT)
                        .text(message.substring(lastIndex, matcher.start()))
                        .build());
            }

            String token = matcher.group(1);
            String code = normalizeEmoteToken(token);
            Emote emote = emoteMap.get(code);

            if (emote != null) {
                fragments.add(MessageFragmentDTO.builder()
                        .type(MessageFragmentType.EMOTE)
                        .text(token)
                        .emoteCode(code)
                        .imageUrl(emote.getImageUrl())
                        .build());
            } else {
                fragments.add(MessageFragmentDTO.builder()
                        .type(MessageFragmentType.TEXT)
                        .text(token)
                        .build());
            }

            lastIndex = matcher.end();
        }

        if (lastIndex < message.length()) {
            fragments.add(MessageFragmentDTO.builder()
                    .type(MessageFragmentType.TEXT)
                    .text(message.substring(lastIndex))
                    .build());
        }

        return fragments;
    }

    private String normalizeEmoteToken(String token) {
        return token == null ? "" : token.replaceAll("^:+|:+$", "");
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