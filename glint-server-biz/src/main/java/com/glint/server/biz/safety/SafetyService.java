package com.glint.server.biz.safety;

import com.glint.server.biz.auth.ResourceAccessDeniedException;

import com.glint.server.common.safety.SafetyContracts.MessageReport;
import com.glint.server.dal.safety.MessageReportEntity;
import com.glint.server.dal.safety.MessageReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@Service
/** 创建限时保留的消息举报记录。 */
public class SafetyService {
    private static final Logger log = LoggerFactory.getLogger(SafetyService.class);

    private final MessageReportRepository messageReportRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RetentionPolicy retentionPolicy = new RetentionPolicy();

    @Autowired
    public SafetyService(MessageReportRepository messageReportRepository, JdbcTemplate jdbcTemplate) {
        this.messageReportRepository = messageReportRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public SafetyService(MessageReportRepository messageReportRepository) {
        this(messageReportRepository, null);
    }

    @Transactional
    public MessageReport report(String messageId, String reporterId, String reason) {
        verifyMessageAccess(messageId, reporterId);
        Instant createdAt = Instant.now();
        String reportId = UUID.randomUUID().toString();
        messageReportRepository.save(MessageReportEntity.pending(
                reportId, messageId, reporterId, reason, createdAt, retentionPolicy.messageExpiresAt(createdAt)
        ));
        log.info("message_report_persisted reporterId={}, reportId={}, messageId={}, reasonLength={}", shortId(reporterId), shortId(reportId), shortId(messageId), reason.length());
        return new MessageReport(reportId, messageId, reporterId, reason, "PENDING", createdAt);
    }

    private void verifyMessageAccess(String messageId, String reporterId) {
        if (jdbcTemplate == null) return;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_chat_messages message
                JOIN t_chat_sessions session ON session.id = message.session_id
                JOIN t_match_events event ON event.id = session.match_event_id
                WHERE message.id = ? AND (event.first_user_id = ? OR event.second_user_id = ?)
                """, Integer.class, messageId, reporterId, reporterId);
        if (count == null || count == 0) throw new ResourceAccessDeniedException("消息不存在或无权举报");
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
