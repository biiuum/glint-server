-- 联调发布就绪：记录手动迁移版本，并保证一个匹配只创建一个聊天会话。
-- 脚本可重复执行；若已有重复会话，先停止并由维护者确认保留哪一个，绝不静默删除数据。

CREATE TABLE IF NOT EXISTS t_schema_versions (
    version VARCHAR(64) PRIMARY KEY COMMENT '已手动应用的数据库变更版本',
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '应用时间'
) COMMENT='数据库手动变更版本记录';

SET @has_match_session_unique_key = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_chat_sessions'
      AND index_name = 'uk_chat_sessions_match_event'
);

SET @add_match_session_unique_key = IF(
    @has_match_session_unique_key = 0,
    'ALTER TABLE t_chat_sessions ADD UNIQUE KEY uk_chat_sessions_match_event (match_event_id)',
    'SELECT 1'
);
PREPARE add_match_session_unique_key_statement FROM @add_match_session_unique_key;
EXECUTE add_match_session_unique_key_statement;
DEALLOCATE PREPARE add_match_session_unique_key_statement;

INSERT IGNORE INTO t_schema_versions (version) VALUES ('2026-08-22-release-readiness');
