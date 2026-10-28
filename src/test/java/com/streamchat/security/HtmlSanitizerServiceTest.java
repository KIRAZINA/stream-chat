package com.streamchat.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1.3: chat messages are plain text, so every HTML vector must be
 * stripped while Unicode, emojis, emote codes, and URLs survive untouched.
 */
class HtmlSanitizerServiceTest {

    private final HtmlSanitizerService sanitizer = new HtmlSanitizerService();

    @Test
    void sanitize_stripsScriptTagsCompletely() {
        String result = sanitizer.sanitize("<script>alert('xss')</script>");

        assertEquals("", result);
        assertFalse(result.contains("<script"));
        assertFalse(result.contains("alert"));
    }

    @Test
    void sanitize_stripsScriptTagsButKeepsSurroundingText() {
        String result = sanitizer.sanitize("<script>alert('xss')</script>Hello world!");

        assertEquals("Hello world!", result);
    }

    @Test
    void sanitize_stripsImageAndEventHandlerVectors() {
        assertEquals("", sanitizer.sanitize("<img src=x onerror=alert(1)>"));
        assertEquals("", sanitizer.sanitize("<svg onload=alert(1)></svg>"));
        assertEquals("", sanitizer.sanitize("<iframe src=\"javascript:alert(1)\"></iframe>"));
        assertEquals("safe", sanitizer.sanitize("<div onclick=\"alert(1)\">safe</div>"));
        assertEquals("", sanitizer.sanitize("<body onload=alert(1)>"));
    }

    @Test
    void sanitize_stripsJavascriptUrlButKeepsLinkText() {
        String result = sanitizer.sanitize("<a href=\"javascript:alert(1)\">click me</a>");

        assertEquals("click me", result);
        assertFalse(result.contains("javascript:"));
    }

    @Test
    void sanitize_stripsEntityEncodedMarkup() {
        assertEquals("", sanitizer.sanitize("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertEquals("", sanitizer.sanitize("&#60;img src=x onerror=alert(1)&#62;"));
    }

    @Test
    void sanitize_stripsStyleTags() {
        String result = sanitizer.sanitize("<style>body{background:url('javascript:alert(1)')}</style>Text");

        assertEquals("Text", result);
    }

    @Test
    void sanitize_preservesNormalTextEmojisAndEmoteCodes() {
        String content = "Hello world! :smile: :pepeKek: 👋 🎉";

        assertEquals(content, sanitizer.sanitize(content));
    }

    @Test
    void sanitize_preservesUnicodePunctuationAndAmpersands() {
        String content = "Tom & Jerry's 100% fun — “quoted” café naïve";

        assertEquals(content, sanitizer.sanitize(content));
    }

    @Test
    void sanitize_preservesUrls() {
        String content = "Check https://example.com/path?a=1&b=2#frag";

        assertEquals(content, sanitizer.sanitize(content));
    }

    @Test
    void sanitize_preservesComparisonSymbolsAsPlainText() {
        assertEquals("1 < 2 and 3 > 2", sanitizer.sanitize("1 < 2 and 3 > 2"));
    }

    @Test
    void sanitize_trimsLeadingAndTrailingWhitespace() {
        assertEquals("hello", sanitizer.sanitize("   hello   "));
        assertEquals("hello", sanitizer.sanitize("\n\thello\t\n"));
    }

    @Test
    void sanitize_handlesNullAndBlankGracefully() {
        assertNull(sanitizer.sanitize(null));
        assertEquals("", sanitizer.sanitize(""));
        assertEquals("", sanitizer.sanitize("   "));
        assertEquals("", sanitizer.sanitize("\n\t "));
    }

    @Test
    void sanitize_isIdempotentForAlreadyPlainText() {
        String content = "Hello :wave: 👋";

        assertTrue(sanitizer.sanitize(sanitizer.sanitize(content)).equals(content));
    }
}
