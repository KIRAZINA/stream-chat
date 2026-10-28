package com.streamchat.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Externalized Caffeine cache tuning for the chat hot paths.
 *
 * <p>Every cache domain used by {@code ChatService} has an independent TTL and
 * maximum size so operators can tune memory usage without a rebuild. Values are
 * bound from {@code app.cache.*} (see {@code application.properties}).</p>
 */
@Component
@ConfigurationProperties(prefix = "app.cache")
@Getter
@Setter
public class CacheProperties {

    /** Badge lookups per (user, stream) pair. */
    private CacheSpec userBadges = new CacheSpec(Duration.ofMinutes(5), 1000);

    /** Message fragmentation results keyed by message content. */
    private CacheSpec emoteFragments = new CacheSpec(Duration.ofMinutes(5), 500);

    /** Emote maps keyed by stream id. */
    private CacheSpec streamEmotes = new CacheSpec(Duration.ofMinutes(5), 1000);

    /**
     * Per-cache tuning knobs. Defaults mirror the historical hardcoded values.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheSpec {

        private Duration ttl = Duration.ofMinutes(5);

        private long maxSize = 1000;
    }
}
