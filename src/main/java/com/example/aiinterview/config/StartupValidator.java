package com.example.aiinterview.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

// Graceful: use ObjectProvider to avoid Bean creation failure when external services are down
@Component
public class StartupValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final DataSource dataSource;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.redis.host}")
    private String redisHost;

    public StartupValidator(DataSource dataSource,
                            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        this.dataSource = dataSource;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public void run(String... args) {
        log.info("=== Startup Self-Check ===");

        // 1. MySQL connection
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                log.info("[Startup] MySQL connected - {}", datasourceUrl);
            } else {
                throw new IllegalStateException("[Startup Failed] MySQL connection invalid");
            }
        } catch (Exception e) {
            throw new IllegalStateException("[Startup Failed] MySQL connection error: " + e.getMessage(), e);
        }

        // 3. Redis connection
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("[Startup] RedisTemplate unavailable, skipping Redis validation");
        } else {
            try {
                String pingResult = redisTemplate.getConnectionFactory().getConnection().ping();
                if (pingResult != null) {
                    log.info("[Startup] Redis connected - {}:{}", redisHost, 6379);
                } else {
                    throw new IllegalStateException("[Startup Failed] Redis connection error");
                }
            } catch (Exception e) {
                throw new IllegalStateException("[Startup Failed] Redis connection failed: " + e.getMessage(), e);
            }
        }

        log.info("=== All Startup Checks Passed ===");
    }
}
