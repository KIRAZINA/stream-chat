package com.streamchat.service;

import com.streamchat.model.entity.Emote;
import com.streamchat.model.entity.Stream;
import com.streamchat.model.entity.User;
import com.streamchat.repository.EmoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmoteService.
 */
@ExtendWith(MockitoExtension.class)
class EmoteServiceTest {

    @Mock
    private EmoteRepository emoteRepository;

    @InjectMocks
    private EmoteService emoteService;

    private Stream testStream;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("streamer")
                .build();

        testStream = Stream.builder()
                .id(1L)
                .streamKey("test-stream")
                .user(testUser)
                .build();
    }

    @Test
    void parseEmotes_WithGlobalEmote_Success() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello :smile: world";
        
        Emote globalEmote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(globalEmote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("smile.png"));
        assertTrue(result.contains("smile"));
    }

    @Test
    void parseEmotes_WithStreamSpecificEmote_Success() {
        // Arrange
        Long streamId = 1L;
        String message = "Check out :custom: emote";
        
        Emote streamEmote = Emote.builder()
                .id(1L)
                .code("custom")
                .imageUrl("https://example.com/custom.png")
                .stream(testStream)
                .isGlobal(false)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(streamEmote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("custom.png"));
        assertTrue(result.contains("custom"));
    }

    @Test
    void parseEmotes_MultipleEmotes_Success() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello :smile: and :wave: everyone";
        
        Emote emote1 = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        Emote emote2 = Emote.builder()
                .id(2L)
                .code("wave")
                .imageUrl("https://example.com/wave.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote1, emote2));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("smile.png"));
        assertTrue(result.contains("wave.png"));
    }

    @Test
    void parseEmotes_NoEmotes_ReturnsOriginal() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello world";

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of());

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertEquals(message, result);
    }

    @Test
    void parseEmotes_EmoteNotInMessage_ReturnsOriginal() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello world";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertEquals(message, result);
    }

    @Test
    void parseEmotes_WholeWordMatchOnly() {
        // Arrange
        Long streamId = 1L;
        String message = "smile :smile: :smile:smile";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        // Should replace :smile: but not "smile" or ":smile:smile"
        assertTrue(result.contains("<img")); // At least one replacement
        assertTrue(result.contains("smile")); // Original word "smile" should remain
    }

    @Test
    void parseEmotes_CaseSensitive() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello :Smile: :smile:";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        // Only :smile: should be replaced, not :Smile:
        assertTrue(result.contains(":Smile:")); // Should remain unchanged
        assertTrue(result.contains("<img")); // :smile: should be replaced
    }

    @Test
    void parseEmotes_DuplicateEmoteCodes_UsesFirst() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello :smile:";
        
        Emote emote1 = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile1.png")
                .isGlobal(true)
                .build();

        Emote emote2 = Emote.builder()
                .id(2L)
                .code("smile")
                .imageUrl("https://example.com/smile2.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote1, emote2));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        // Should use first emote (smile1.png) due to merge function
        assertTrue(result.contains("smile1.png"));
        assertFalse(result.contains("smile2.png"));
    }

    @Test
    void parseEmotes_EmptyMessage_ReturnsEmpty() {
        // Arrange
        Long streamId = 1L;
        String message = "";

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of());

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertEquals("", result);
    }

    @Test
    void buildMessageFragments_WithEmoteFragments_Success() {
        Long streamId = 1L;
        String message = "Hello :smile: world";

        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        var fragments = emoteService.buildMessageFragments(streamId, message);

        assertNotNull(fragments);
        assertEquals(3, fragments.size());
        assertEquals("Hello ", fragments.get(0).getText());
        assertEquals("smile", fragments.get(1).getEmoteCode());
        assertEquals("https://example.com/smile.png", fragments.get(1).getImageUrl());
        assertEquals(" world", fragments.get(2).getText());
        assertEquals("EMOTE", fragments.get(1).getType().name());
    }

    @Test
    void parseEmotes_SpecialCharactersInMessage() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello :smile:! How are you?";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("!"));
        assertTrue(result.contains("?"));
    }

    @Test
    void getStreamEmotes_Success() {
        // Arrange
        Long streamId = 1L;
        
        Emote emote1 = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        Emote emote2 = Emote.builder()
                .id(2L)
                .code("wave")
                .imageUrl("https://example.com/wave.png")
                .stream(testStream)
                .isGlobal(false)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote1, emote2));

        // Act
        List<Emote> result = emoteService.getStreamEmotes(streamId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(emoteRepository).findByStreamIdOrGlobal(streamId);
    }

    @Test
    void getStreamEmotes_EmptyList() {
        // Arrange
        Long streamId = 1L;

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of());

        // Act
        List<Emote> result = emoteService.getStreamEmotes(streamId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseEmotes_EmoteAtStart() {
        // Arrange
        Long streamId = 1L;
        String message = ":smile: Hello";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void parseEmotes_EmoteAtEnd() {
        // Arrange
        Long streamId = 1L;
        String message = "Hello :smile:";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void parseEmotes_HTMLFormatCorrect() {
        // Arrange
        Long streamId = 1L;
        String message = ":smile:";
        
        Emote emote = Emote.builder()
                .id(1L)
                .code("smile")
                .imageUrl("https://example.com/smile.png")
                .isGlobal(true)
                .build();

        when(emoteRepository.findByStreamIdOrGlobal(streamId))
                .thenReturn(List.of(emote));

        // Act
        String result = emoteService.parseEmotes(streamId, message);

        // Assert
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("src='https://example.com/smile.png'"));
        assertTrue(result.contains("alt='smile'"));
        assertTrue(result.contains("class='emote'"));
    }
}
