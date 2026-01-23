package com.streamchat.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Configuration for application caching.
 * Provides in-memory cache for frequently accessed data.
 * Can be replaced with Redis cache for distributed environments.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure cache manager with predefined caches.
     * Uses concurrent hash maps for thread-safe in-memory caching.
     *
     * @return configured cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(Arrays.asList(
                // Cache for recent chat messages
                new ConcurrentMapCache("recentMessages"),

                // Cache for user rate limits
                new ConcurrentMapCache("rateLimits"),

                // Cache for banned users
                new ConcurrentMapCache("bannedUsers"),

                // Cache for timed out users
                new ConcurrentMapCache("timedOutUsers"),

                // Cache for stream settings
                new ConcurrentMapCache("streamSettings"),

                // Cache for user permissions
                new ConcurrentMapCache("userPermissions"),

                // Cache for emotes
                new ConcurrentMapCache("emotes"),

                // Cache for blocked words
                new ConcurrentMapCache("blockedWords")
        ));

        return cacheManager;
    }
}