package com.streamchat.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.streamchat.config.CacheProperties;
import com.streamchat.model.dto.EmoteDTO;
import com.streamchat.model.dto.MessageFragmentDTO;
import com.streamchat.model.entity.Emote;
import com.streamchat.model.enums.MessageFragmentType;
import com.streamchat.repository.EmoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmoteService {

    private final EmoteRepository emoteRepository;
    private final CacheProperties cacheProperties;
    private final Cache<Long, List<Emote>> streamEmoteCache;

    public EmoteService(EmoteRepository emoteRepository, CacheProperties cacheProperties) {
        this.emoteRepository = emoteRepository;
        this.cacheProperties = cacheProperties;
        CacheProperties.CacheSpec spec = (cacheProperties != null) ? cacheProperties.getStreamEmotes() : null;
        if (spec == null) {
            spec = new CacheProperties.CacheSpec(java.time.Duration.ofMinutes(5), 1000);
        }
        this.streamEmoteCache = Caffeine.newBuilder()
                .expireAfterWrite(spec.getTtl())
                .maximumSize(spec.getMaxSize())
                .build();
    }

    public String parseEmotes(Long streamId, String message) {
        List<Emote> emotes = getStreamEmotes(streamId);
        Map<String, String> emoteMap = emotes.stream()
                .collect(Collectors.toMap(Emote::getCode,
                        e -> String.format("<img src='%s' alt='%s' class='emote' />", e.getImageUrl(), e.getCode()),
                        (a, b) -> a));
        String parsed = message;
        for (Map.Entry<String, String> entry : emoteMap.entrySet()) {
            parsed = parsed.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
        }
        return parsed;
    }

    public List<Emote> getStreamEmotes(Long streamId) {
        return streamEmoteCache.get(streamId, id -> emoteRepository.findByStreamIdOrGlobal(id));
    }

    public List<Emote> getEmotesForStream(Long streamId) {
        return getStreamEmotes(streamId);
    }

    public List<Emote> getGlobalEmotes() {
        return streamEmoteCache.get(0L, id -> emoteRepository.findByStreamIdOrGlobal(null));
    }

    public List<EmoteDTO> toDto(List<Emote> emotes) {
        return emotes.stream().map(e -> EmoteDTO.builder()
                .id(e.getId())
                .code(e.getCode())
                .imageUrl(e.getImageUrl())
                .isGlobal(Boolean.TRUE.equals(e.getIsGlobal()))
                .streamId(e.getStream() != null ? e.getStream().getId() : null)
                .build()).collect(Collectors.toList());
    }

    public List<MessageFragmentDTO> buildMessageFragments(Long streamId, String message) {
        if (message == null || message.isEmpty()) {
            return List.of();
        }

        List<Emote> emotes = getEmotesForStream(streamId);
        Map<String, Emote> emoteMap = emotes.stream()
                .collect(Collectors.toMap(Emote::getCode, e -> e, (a, b) -> a));

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
            String code = token.replaceAll("^:+|:+$", "");
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
}
