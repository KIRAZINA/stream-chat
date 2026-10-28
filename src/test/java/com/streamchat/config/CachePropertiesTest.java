package com.streamchat.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.streamchat.repository.ChatMessageRepository;
import com.streamchat.repository.EmoteRepository;
import com.streamchat.repository.StreamRepository;
import com.streamchat.repository.UserBadgeRepository;
import com.streamchat.repository.UserRepository;
import com.streamchat.repository.UserStreamRoleRepository;
import com.streamchat.service.AutoModService;
import com.streamchat.service.ChatMessagePersister;
import com.streamchat.service.ChatService;
import com.streamchat.service.EmoteService;
import com.streamchat.service.MetricsService;
import com.streamchat.service.ModerationService;
import com.streamchat.service.RateLimitService;
import com.streamchat.service.StreamAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that Caffeine tuning is bound from {@code app.cache.*} and that
 * {@link ChatService} actually applies those values to its caches.
 */
class CachePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultCacheSettings() {
        runner.run(context -> {
            CacheProperties properties = context.getBean(CacheProperties.class);

            assertThat(properties.getUserBadges().getTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getUserBadges().getMaxSize()).isEqualTo(1000);
            assertThat(properties.getEmoteFragments().getTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getEmoteFragments().getMaxSize()).isEqualTo(500);
            assertThat(properties.getStreamEmotes().getTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getStreamEmotes().getMaxSize()).isEqualTo(1000);
        });
    }

    @Test
    void customPropertiesOverrideTtlAndMaxSize() {
        runner.withPropertyValues(
                "app.cache.user-badges.ttl=90s",
                "app.cache.user-badges.max-size=42",
                "app.cache.emote-fragments.ttl=2m",
                "app.cache.emote-fragments.max-size=7",
                "app.cache.stream-emotes.ttl=PT30S",
                "app.cache.stream-emotes.max-size=99"
        ).run(context -> {
            CacheProperties properties = context.getBean(CacheProperties.class);

            assertThat(properties.getUserBadges().getTtl()).isEqualTo(Duration.ofSeconds(90));
            assertThat(properties.getUserBadges().getMaxSize()).isEqualTo(42);
            assertThat(properties.getEmoteFragments().getTtl()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.getEmoteFragments().getMaxSize()).isEqualTo(7);
            assertThat(properties.getStreamEmotes().getTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getStreamEmotes().getMaxSize()).isEqualTo(99);
        });
    }

    @Test
    void chatServiceBuildsCachesFromConfiguredValues() {
        CacheProperties properties = new CacheProperties();
        properties.getUserBadges().setTtl(Duration.ofSeconds(30));
        properties.getUserBadges().setMaxSize(11);
        properties.getEmoteFragments().setTtl(Duration.ofSeconds(45));
        properties.getEmoteFragments().setMaxSize(12);
        properties.getStreamEmotes().setTtl(Duration.ofMinutes(2));
        properties.getStreamEmotes().setMaxSize(13);

        ChatService chatService = new ChatService(
                mock(ChatMessageRepository.class),
                mock(StreamRepository.class),
                mock(UserRepository.class),
                mock(RateLimitService.class),
                mock(ModerationService.class),
                mock(StreamAuthorizationService.class),
                mock(UserStreamRoleRepository.class),
                mock(UserBadgeRepository.class),
                mock(EmoteRepository.class),
                mock(EmoteService.class),
                mock(MetricsService.class),
                mock(AutoModService.class),
                mock(ChatMessagePersister.class),
                properties,
                mock(com.streamchat.service.StreamAnalyticsService.class));

        assertCacheSettings(ReflectionTestUtils.getField(chatService, "userBadgeCache"), 30, 11);
        assertCacheSettings(ReflectionTestUtils.getField(chatService, "emoteFragmentCache"), 45, 12);
        assertCacheSettings(ReflectionTestUtils.getField(chatService, "streamEmoteCache"), 120, 13);
    }

    @SuppressWarnings("unchecked")
    private static void assertCacheSettings(Object cacheObject, long ttlSeconds, long maxSize) {
        Cache<Object, Object> cache = (Cache<Object, Object>) cacheObject;

        assertThat(cache.policy().expireAfterWrite().orElseThrow()
                .getExpiresAfter(TimeUnit.SECONDS)).isEqualTo(ttlSeconds);
        assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(maxSize);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CacheProperties.class)
    static class TestConfig {
    }
}
