package com.streamchat.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the production slow-mode Lua script against a real Redis
 * server: cross-instance enforcement, atomic check-and-set, and TTL
 * expiry. Skipped entirely when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class SlowModeRedisIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    private static RedisTemplate<String, Object> redisTemplate;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
    }

    private long runScript(String key, String now, long slowSeconds, long ttlSeconds) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> {
            Object result = connection.eval(
                    ChatService.SLOW_MODE_LUA.getBytes(StandardCharsets.UTF_8),
                    ReturnType.INTEGER,
                    1,
                    key.getBytes(StandardCharsets.UTF_8),
                    now.getBytes(StandardCharsets.UTF_8),
                    String.valueOf(slowSeconds * 1000L).getBytes(StandardCharsets.UTF_8),
                    String.valueOf(ttlSeconds).getBytes(StandardCharsets.UTF_8));
            return result instanceof Number number ? number.longValue() : 0L;
        });
    }

    @Test
    void crossInstance_SecondSendIsBlockedAndThirdAllowedAfterWindow() {
        String key = "slowmode:lastmessage:42:7";
        long t0 = System.currentTimeMillis();

        // instance A sends at t0 -> allowed, timestamp recorded
        assertEquals(0L, runScript(key, String.valueOf(t0), 2L, 7L));

        // instance B sends within the window -> blocked with a wait remaining
        long blocked = runScript(key, String.valueOf(t0 + 100L), 2L, 7L);
        assertTrue(blocked > 0L, "second immediate attempt should be blocked");
        assertTrue(blocked <= 2000L);

        // after the window (simulated later clock) -> allowed again
        assertEquals(0L, runScript(key, String.valueOf(t0 + 5000L), 2L, 7L));
    }

    @Test
    void ttl_EntryExpiresAfterSlowModeSecondsPlusBuffer() throws Exception {
        String key = "slowmode:lastmessage:43:8";
        assertEquals(0L, runScript(key, String.valueOf(System.currentTimeMillis()), 2L, 1L));
        Thread.sleep(1600L);
        // key expired after TTL=1s -> treated as a fresh send
        assertEquals(0L, runScript(key, String.valueOf(System.currentTimeMillis()), 2L, 1L));
    }

    @Test
    void atomicity_ConcurrentSendsAllowExactlyOne() throws Exception {
        String key = "slowmode:lastmessage:44:9";
        long base = System.currentTimeMillis();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            long now = base + i * 5L;
            futures.add(executor.submit(() -> {
                start.await();
                return runScript(key, String.valueOf(now), 600L, 605L);
            }));
        }
        start.countDown();

        long allowed = 0;
        for (Future<Long> future : futures) {
            if (future.get() == 0L) {
                allowed++;
            }
        }
        executor.shutdown();

        assertEquals(1L, allowed, "exactly one concurrent send should win the slow-mode slot");
    }
}
