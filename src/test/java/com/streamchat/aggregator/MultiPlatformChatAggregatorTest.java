package com.streamchat.aggregator;

import com.streamchat.config.AggregatorProperties;
import com.streamchat.security.HtmlSanitizerService;
import com.streamchat.service.AutoModService;
import com.streamchat.service.MessageBroadcastService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiPlatformChatAggregatorTest {

    @Test
    void ingestExternalMessage_sanitizesTagsPlatformAndBroadcasts() {
        HtmlSanitizerService sanitizer = mock(HtmlSanitizerService.class);
        AutoModService autoMod = mock(AutoModService.class);
        MessageBroadcastService broadcast = mock(MessageBroadcastService.class);
        AggregatorProperties props = new AggregatorProperties();
        props.setEnabled(true);
        props.setPersistExternalMessages(false);

        when(sanitizer.sanitize("<b>hi</b>")).thenReturn("hi");
        when(autoMod.processMessage(any(), any(), any()))
                .thenReturn(AutoModService.AutoModResult.approved());

        MultiPlatformChatAggregator agg = new MultiPlatformChatAggregator(
                sanitizer, autoMod, broadcast, props);

        ExternalChatMessagePayload payload = ExternalChatMessagePayload.builder()
                .platform(ExternalPlatform.TWITCH)
                .externalSenderDisplayName("twitchUser")
                .content("<b>hi</b>")
                .build();

        var dto = agg.ingestExternalMessage(payload, "stream1");
        assertNotNull(dto);
        assertEquals("hi", dto.getContent());
        assertEquals(ExternalPlatform.TWITCH, dto.getPlatform());
        verify(broadcast).broadcastMessage(any(), any(), Mockito.eq(false));
    }

    @Test
    void ingestExternalMessage_toxicRejected() {
        HtmlSanitizerService sanitizer = mock(HtmlSanitizerService.class);
        AutoModService autoMod = mock(AutoModService.class);
        MessageBroadcastService broadcast = mock(MessageBroadcastService.class);
        AggregatorProperties props = new AggregatorProperties();
        props.setEnabled(true);

        when(sanitizer.sanitize("bad")).thenReturn("bad");
        AutoModService.AutoModResult rejected = AutoModService.AutoModResult.builder()
                .approved(false).rejectionReason("BLOCKED_WORD").build();
        when(autoMod.processMessage(any(), any(), any())).thenReturn(rejected);

        MultiPlatformChatAggregator agg = new MultiPlatformChatAggregator(
                sanitizer, autoMod, broadcast, props);

        ExternalChatMessagePayload payload = ExternalChatMessagePayload.builder()
                .platform(ExternalPlatform.YOUTUBE)
                .content("bad")
                .build();

        var dto = agg.ingestExternalMessage(payload, "stream1");
        org.junit.jupiter.api.Assertions.assertNull(dto);
    }
}
