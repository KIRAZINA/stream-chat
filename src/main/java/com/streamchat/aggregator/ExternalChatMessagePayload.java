package com.streamchat.aggregator;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalChatMessagePayload {
    private ExternalPlatform platform;
    private String externalMessageId;
    private String externalChannelId;
    private String externalSenderId;
    private String externalSenderUsername;
    private String externalSenderDisplayName;
    private String externalAvatarUrl;
    private String content;
    private LocalDateTime timestamp;
    private Map<String, String> platformMetadata;
}
