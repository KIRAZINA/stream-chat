package com.streamchat.aggregator;

import com.streamchat.config.AggregatorProperties;
import com.streamchat.model.dto.ChatMessageDTO;
import com.streamchat.model.enums.MessageType;
import com.streamchat.security.HtmlSanitizerService;
import com.streamchat.service.AutoModService;
import com.streamchat.service.ChatMessagePersister;
import com.streamchat.service.MessageBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultiPlatformChatAggregator {

    private final HtmlSanitizerService htmlSanitizerService;
    private final AutoModService autoModService;
    private final MessageBroadcastService messageBroadcastService;
    private final AggregatorProperties aggregatorProperties;

    public ChatMessageDTO ingestExternalMessage(ExternalChatMessagePayload payload, String streamKey) {
        if (payload == null || !aggregatorProperties.isEnabled()) {
            return null;
        }
        try {
            String sanitized = htmlSanitizerService.sanitize(payload.getContent());

            AutoModService.AutoModResult modResult = autoModService.processMessage(
                    sanitized, null, null);
            if (!modResult.isApproved()) {
                log.debug("External message rejected by AutoMod: {}", modResult.getRejectionReason());
                return null;
            }

            ChatMessageDTO dto = ChatMessageDTO.builder()
                    .username(payload.getExternalSenderDisplayName() != null
                            ? payload.getExternalSenderDisplayName()
                            : payload.getExternalSenderUsername())
                    .content(sanitized)
                    .messageType(MessageType.CHAT)
                    .platform(payload.getPlatform())
                    .externalAvatarUrl(payload.getExternalAvatarUrl())
                    .build();

            if (aggregatorProperties.isPersistExternalMessages()) {
                log.debug("External message persistence enabled but not implemented; broadcasting only");
            }

            messageBroadcastService.broadcastMessage(streamKey, dto, false);
            return dto;
        } catch (Exception e) {
            log.warn("External message ingestion failed: {}", e.getMessage());
            return null;
        }
    }
}
