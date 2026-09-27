package com.glint.server.biz.safety;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnBean(JdbcTemplate.class)
/** 定期删除超过 PRD 留存期限的业务记录。 */
public class ExpiredDataCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ExpiredDataCleanupService.class);

    private final JdbcTemplate jdbcTemplate;

    public ExpiredDataCleanupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${glint.retention.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void deleteExpiredRecords() {
        int reports = jdbcTemplate.update("""
                DELETE report FROM t_message_reports report
                INNER JOIN t_chat_messages message ON report.message_id = message.id
                WHERE message.expires_at <= CURRENT_TIMESTAMP
                """);
        int messages = jdbcTemplate.update("DELETE FROM t_chat_messages WHERE expires_at <= CURRENT_TIMESTAMP");
        int states = jdbcTemplate.update("""
                DELETE state FROM t_match_unlock_states state
                INNER JOIN t_match_events event ON state.match_event_id = event.id
                WHERE event.expires_at <= CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                      SELECT 1 FROM t_chat_sessions session
                      WHERE session.match_event_id = event.id
                        AND session.reply_window_ends_at > CURRENT_TIMESTAMP
                  )
                """);
        int events = jdbcTemplate.update("""
                DELETE event FROM t_match_events event
                WHERE event.expires_at <= CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                      SELECT 1 FROM t_chat_sessions session
                      WHERE session.match_event_id = event.id
                        AND session.reply_window_ends_at > CURRENT_TIMESTAMP
                  )
                """);
        int chatSessions = jdbcTemplate.update("""
                DELETE session FROM t_chat_sessions session
                WHERE session.reply_window_ends_at <= CURRENT_TIMESTAMP
                  AND NOT EXISTS (
                      SELECT 1 FROM t_chat_messages message WHERE message.session_id = session.id
                  )
                """);
        int summaries = jdbcTemplate.update("DELETE FROM t_commute_station_summaries WHERE expires_at <= CURRENT_TIMESTAMP");
        int sessions = jdbcTemplate.update("DELETE FROM t_anonymous_sessions WHERE expires_at <= CURRENT_TIMESTAMP OR revoked_at IS NOT NULL");
        log.info("retention_cleanup_completed reports={}, messages={}, unlockStates={}, matchEvents={}, chatSessions={}, summaries={}, sessions={}", reports, messages, states, events, chatSessions, summaries, sessions);
    }
}
