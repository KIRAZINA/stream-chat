package com.streamchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Custom health indicator for database and Redis.
 * Provides detailed health status for each dependency.
 */
@Component("customHealth")
@Slf4j
public class CustomHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public CustomHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.unknown();

        // Check database
        builder.withDetail("database", checkDatabase());

        // Check Redis
        builder.withDetail("redis", checkRedis());

        return builder.build();
    }

    private Object checkDatabase() {
        try {
            dataSource.getConnection().close();
            return "UP";
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return "DOWN: " + e.getMessage();
        }
    }

    private Object checkRedis() {
        if (redisTemplate == null) {
            return "DISABLED";
        }

        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return "UP";
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return "DOWN: " + e.getMessage();
        }
    }
}
