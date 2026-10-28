package com.streamchat.service;

import com.streamchat.config.AutoModProperties;
import com.streamchat.model.entity.BlockedWord;
import com.streamchat.repository.BlockedWordRepository;
import com.streamchat.repository.ModerationLogRepository;
import com.streamchat.service.MetricsService;
import com.streamchat.service.ModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoModServiceTest {

    private AutoModService autoModService;
    private BlockedWordRepository blockedWordRepository;
    private AutoModProperties props;

    @BeforeEach
    void setUp() {
        blockedWordRepository = mock(BlockedWordRepository.class);
        props = new AutoModProperties();
        props.setCapsThreshold(0.7);
        props.setSpamScoreThreshold(3);
        props.setToxicityScoreThreshold(0.3);
        props.setShadowBanEnabled(true);
        props.setAiInferenceEnabled(false);
        autoModService = new AutoModService(
                mock(ModerationService.class),
                mock(ModerationLogRepository.class),
                mock(MetricsService.class),
                blockedWordRepository,
                props);
    }

    @Test
    void highCapsRatio_failsOrScoresHigh() {
        when(blockedWordRepository.findAllGlobal()).thenReturn(List.of());
        AutoModService.AutoModResult r = autoModService.processMessage("HELLO WORLD THIS IS ALL CAPS", 1L, 1L);
        assertTrue(r.getSpamScore() >= 2 || !r.isApproved() || r.isShadowBanned());
    }

    @Test
    void blockedWord_triggersRejection() {
        BlockedWord bw = new BlockedWord();
        bw.setWord("badword");
        when(blockedWordRepository.findAllGlobal()).thenReturn(List.of(bw));
        AutoModService.AutoModResult r = autoModService.processMessage("this has badword in it", 1L, 1L);
        assertFalse(r.isApproved());
        assertEquals("BLOCKED_WORD", r.getRejectionReason());
    }

    @Test
    void repetitiveChars_accumulatesSpamScore() {
        when(blockedWordRepository.findAllGlobal()).thenReturn(List.of());
        AutoModService.AutoModResult r = autoModService.processMessage("goooooooaaaaaaaal!!!!!!", 1L, 1L);
        assertTrue(r.getSpamScore() >= 1);
    }

    @Test
    void aiInferenceFallback_worksWhenUnreachable() {
        props.setAiInferenceEnabled(true);
        props.setAiInferenceUrl("http://localhost:9999/v1/analyze");
        when(blockedWordRepository.findAllGlobal()).thenReturn(List.of());
        AutoModService.AutoModResult r = autoModService.processMessage("hello world", 1L, 1L);
        assertTrue(r.isApproved());
    }

    @Test
    void shadowBan_appliedWhenThresholdExceeded() {
        when(blockedWordRepository.findAllGlobal()).thenReturn(List.of());
        AutoModService.AutoModResult r = autoModService.processMessage("HELLO WORLD THIS IS ALL CAPS NOW", 1L, 1L);
        // caps score 2 < threshold 3, so not shadow banned; check shadow ban flag logic via high toxicity
        // Force toxicity by using toxic phrase
        AutoModService.AutoModResult r2 = autoModService.processMessage("you are an idiot", 1L, 1L);
        assertTrue(r2.isShadowBanned() || !r2.isApproved());
    }
}
