package com.glint.server.biz.system;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 检查联调依赖与手动数据库版本，不用常量伪造健康状态。 */
@Service
public class SystemStatusService {

    public static final String EXPECTED_SCHEMA_VERSION = "2026-08-22-release-readiness";
    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);
    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public SystemStatusService(JdbcTemplate jdbcTemplate, RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /** 只有数据库、Redis 和预期 schema 版本都可用时才返回 UP。 */
    public SystemStatus currentStatus() {
        String database = databaseStatus();
        String schemaVersion = schemaVersion();
        String redis = redisStatus();
        boolean ready = "UP".equals(database)
                && "UP".equals(redis)
                && EXPECTED_SCHEMA_VERSION.equals(schemaVersion);
        SystemStatus status = new SystemStatus(
                ready ? "UP" : "DOWN",
                database,
                redis,
                schemaVersion,
                EXPECTED_SCHEMA_VERSION
        );
        log.info("system_status_calculated status={}, database={}, redis={}, schemaReady={}",
                status.status(), database, redis, EXPECTED_SCHEMA_VERSION.equals(schemaVersion));
        return status;
    }

    private String databaseStatus() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1 ? "UP" : "DOWN";
        } catch (RuntimeException exception) {
            log.warn("system_database_check_failed exceptionType={}", exception.getClass().getSimpleName());
            return "DOWN";
        }
    }

    private String schemaVersion() {
        try {
            String version = jdbcTemplate.queryForObject("SELECT MAX(version) FROM t_schema_versions", String.class);
            return version == null || version.isBlank() ? "MISSING" : version;
        } catch (RuntimeException exception) {
            log.warn("system_schema_check_failed exceptionType={}", exception.getClass().getSimpleName());
            return "MISSING";
        }
    }

    private String redisStatus() {
        try (var connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "DOWN";
        } catch (RuntimeException exception) {
            log.warn("system_redis_check_failed exceptionType={}", exception.getClass().getSimpleName());
            return "DOWN";
        }
    }
}
