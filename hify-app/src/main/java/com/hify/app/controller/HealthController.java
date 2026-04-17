package com.hify.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private static final String UP      = "UP";
    private static final String DOWN    = "DOWN";
    private static final String SKIPPED = "skipped";

    private final DataSource dataSource;
    private final Optional<RedisTemplate<String, Object>> redisTemplate;

    @Value("${spring.pgvector.host:}")
    private String pgHost;
    @Value("${spring.pgvector.port:5432}")
    private int pgPort;
    @Value("${spring.pgvector.database:hify}")
    private String pgDatabase;
    @Value("${spring.pgvector.username:}")
    private String pgUsername;
    @Value("${spring.pgvector.password:}")
    private String pgPassword;

    public HealthController(DataSource dataSource,
                            Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("mysql",    checkMysql());
        components.put("redis",    checkRedis());
        components.put("pgvector", checkPgvector());

        boolean allUp = components.values().stream()
                .allMatch(v -> UP.equals(v) || SKIPPED.equals(v));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allUp ? UP : DOWN);
        body.put("components", components);

        HttpStatus status = allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }

    private Object checkMysql() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
            return UP;
        } catch (Exception e) {
            log.warn("action=health_check component=mysql error={}", e.getMessage());
            return Map.of("status", DOWN, "error", e.getMessage());
        }
    }

    private Object checkRedis() {
        if (redisTemplate.isEmpty()) {
            return SKIPPED;
        }
        try {
            String pong = redisTemplate.get().getConnectionFactory()
                    .getConnection().ping();
            return "PONG".equalsIgnoreCase(pong) ? UP : DOWN;
        } catch (Exception e) {
            log.warn("action=health_check component=redis error={}", e.getMessage());
            return Map.of("status", DOWN, "error", e.getMessage());
        }
    }

    private Object checkPgvector() {
        if (pgHost == null || pgHost.isBlank()) {
            return SKIPPED;
        }
        String url = String.format("jdbc:postgresql://%s:%d/%s", pgHost, pgPort, pgDatabase);
        try (Connection conn = DriverManager.getConnection(url, pgUsername, pgPassword);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
            return UP;
        } catch (Exception e) {
            log.warn("action=health_check component=pgvector error={}", e.getMessage());
            return Map.of("status", DOWN, "error", e.getMessage());
        }
    }
}
