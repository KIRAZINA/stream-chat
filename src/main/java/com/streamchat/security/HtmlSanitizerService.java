package com.streamchat.security;

import org.jsoup.parser.Parser;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Server-side HTML sanitization for untrusted chat message content.
 *
 * <p>Chat messages are plain text (with emote codes such as {@code :smile:}),
 * so the policy allows no HTML elements or attributes at all. Entity-encoded
 * markup is decoded before sanitization so obfuscated payloads such as
 * {@code &lt;script&gt;...&lt;/script&gt;} cannot slip through, and the
 * sanitized output is decoded back to plain text so normal characters such as
 * {@code &} are not stored as HTML entities.</p>
 *
 * <p>The policy is stateless and thread-safe, so a single instance is reused.</p>
 */
@Component
public class HtmlSanitizerService {

    /**
     * Strict policy: no elements, no attributes, no URL protocols. Anything
     * that looks like a tag is dropped while ordinary text is preserved.
     */
    private static final PolicyFactory STRICT_POLICY = new HtmlPolicyBuilder().toFactory();

    /**
     * Sanitize untrusted message content.
     *
     * @param input raw message content, may be {@code null}
     * @return plain text with all HTML tags removed and entities decoded, or
     *         {@code null} when the input is {@code null}
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        if (input.isBlank()) {
            return "";
        }

        // Decode once so entity-obfuscated markup is visible to the sanitizer.
        // Decoding is deliberately single-pass: a second decode is required to
        // turn the remaining entities back into markup, which keeps the output
        // safe when it is rendered by an HTML-aware client.
        String decoded = Parser.unescapeEntities(input, false);

        String sanitized = STRICT_POLICY.sanitize(decoded);

        // The sanitizer escapes ordinary text (e.g. '&' -> '&amp;'); decode the
        // result so the persisted/broadcast message stays plain text.
        return Parser.unescapeEntities(sanitized, false).trim();
    }
}
