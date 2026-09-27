package com.glint.server.biz.chat;

import com.glint.server.biz.auth.ResourceAccessDeniedException;

import com.glint.server.common.chat.ChatContracts.Message;
import com.glint.server.common.chat.ChatContracts.Session;
import com.glint.server.common.matching.MatchingContracts.Journey;
import com.glint.server.common.metro.MetroContracts;
import com.glint.server.dal.chat.ChatMessageEntity;
import com.glint.server.dal.chat.ChatMessageRepository;
import com.glint.server.dal.chat.ChatSessionEntity;
import com.glint.server.dal.chat.ChatSessionRepository;
import com.glint.server.biz.safety.RetentionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
/** 限时聊天服务；消息正文只以密文落库且不进入日志。 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final MessageCipher messageCipher;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RetentionPolicy retentionPolicy = new RetentionPolicy();

    @Autowired
    public ChatService(MessageCipher messageCipher, ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository, JdbcTemplate jdbcTemplate) {
        this.messageCipher = messageCipher;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatService(MessageCipher messageCipher, ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository) {
        this(messageCipher, chatSessionRepository, chatMessageRepository, null);
    }

    @Transactional
    public Session createSession(String matchId, String userId) {
        verifyMatchParticipant(matchId, userId);
        var existing = chatSessionRepository.findFirstByMatchEventId(matchId);
        if (existing.isPresent()) {
            return session(existing.get().getId());
        }
        Instant rideEndsAt = Instant.now().plus(Duration.ofMinutes(30));
        Instant replyWindowEndsAt = rideEndsAt.plus(Duration.ofHours(24));
        String sessionId = UUID.randomUUID().toString();
        chatSessionRepository.save(ChatSessionEntity.create(sessionId, matchId, rideEndsAt, replyWindowEndsAt, "RIDING"));
        log.info("chat_session_persisted sessionId={}, matchId={}", shortId(sessionId), shortId(matchId));
        return decorate(new Session(sessionId, matchId, rideEndsAt, replyWindowEndsAt, "RIDING"));
    }

    @Transactional
    public Session session(String sessionId) {
        ChatSessionEntity entity = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("聊天会话不存在"));
        Instant now = Instant.now();
        if (entity.getReplyWindowEndsAt() != null && !entity.getReplyWindowEndsAt().isAfter(now)) entity.setStatus("EXPIRED");
        else if (entity.getRideEndsAt() != null && !entity.getRideEndsAt().isAfter(now)) entity.setStatus("BOTTLE");
        Session session = new Session(entity.getId(), entity.getMatchEventId(), entity.getRideEndsAt(), entity.getReplyWindowEndsAt(), entity.getStatus());
        log.info("chat_session_loaded sessionId={}, status={}", shortId(sessionId), session.status());
        return decorate(session);
    }

    @Transactional
    public Session session(String sessionId, String userId) {
        verifyParticipant(sessionId, userId);
        return session(sessionId);
    }

    @Transactional(readOnly = true)
    public java.util.List<Session> sessions(String userId) {
        return jdbcTemplate.query("""
                SELECT session.id, session.match_event_id, session.ride_ends_at, session.reply_window_ends_at,
                       CASE WHEN session.reply_window_ends_at <= CURRENT_TIMESTAMP THEN 'EXPIRED'
                            WHEN session.ride_ends_at <= CURRENT_TIMESTAMP THEN 'BOTTLE'
                            ELSE session.status END,
                       event.line_id, event.station_code, line.line_name,
                       COALESCE(profile.avatar_key, 'aurora')
                FROM t_chat_sessions session JOIN t_match_events event ON event.id = session.match_event_id
                LEFT JOIN t_metro_lines line ON line.line_id = event.line_id
                LEFT JOIN t_profiles profile ON profile.user_id = CASE
                    WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END
                WHERE event.first_user_id = ? OR event.second_user_id = ?
                ORDER BY session.ride_ends_at DESC
                """, (row, ignored) -> new Session(row.getString(1), row.getString(2), row.getTimestamp(3).toInstant(),
                row.getTimestamp(4).toInstant(), row.getString(5),
                journey(row.getString(6), row.getString(7), row.getString(8)), "神秘乘客", row.getString(9)),
                userId, userId, userId);
    }

    public Message send(String sessionId, String senderId, String text) {
        verifyParticipant(sessionId, senderId);
        Session current = session(sessionId);
        if ("EXPIRED".equals(current.status())) throw new IllegalArgumentException("聊天回复窗口已结束");
        MessageCipher.EncryptedMessage encrypted = messageCipher.encrypt(text);
        String messageId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        chatMessageRepository.save(ChatMessageEntity.encrypted(
                messageId,
                sessionId,
                senderId,
                encrypted.ciphertext(),
                encrypted.nonce(),
                encrypted.keyVersion(),
                createdAt,
                retentionPolicy.messageExpiresAt(createdAt)
        ));
        log.info("chat_message_persisted senderId={}, sessionId={}, messageId={}, textLength={}", shortId(senderId), shortId(sessionId), shortId(messageId), text.length());
        return new Message(messageId, sessionId, senderId, text, createdAt, "BOTTLE".equals(current.status()) ? "BOTTLE" : "ACTIVE");
    }

    @Transactional(readOnly = true)
    public java.util.List<Message> messages(String sessionId, String userId) {
        verifyParticipant(sessionId, userId);
        String status = session(sessionId).status();
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream().map(entity ->
                new Message(entity.getId(), entity.getSessionId(), entity.getSenderId(),
                        messageCipher.decrypt(new MessageCipher.EncryptedMessage(entity.getCiphertext(), entity.getNonce(), entity.getKeyVersion())),
                        entity.getCreatedAt(), "BOTTLE".equals(status) ? "BOTTLE" : "ACTIVE")).toList();
    }

    private void verifyParticipant(String sessionId, String userId) {
        if (jdbcTemplate == null) return;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_chat_sessions session JOIN t_match_events event ON event.id = session.match_event_id
                WHERE session.id = ? AND (event.first_user_id = ? OR event.second_user_id = ?)
                """, Integer.class, sessionId, userId, userId);
        if (count == null || count == 0) throw new ResourceAccessDeniedException("无权访问该聊天会话");
    }

    private void verifyMatchParticipant(String matchId, String userId) {
        if (jdbcTemplate == null) throw new IllegalStateException("聊天会话创建缺少持久化上下文");
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_match_events
                WHERE id = ? AND expires_at > CURRENT_TIMESTAMP
                  AND (first_user_id = ? OR second_user_id = ?)
                """, Integer.class, matchId, userId, userId);
        if (count == null || count == 0) throw new ResourceAccessDeniedException("匹配不存在、已过期或无权创建聊天会话");
    }

    private Session decorate(Session base) {
        if (jdbcTemplate == null) return base;
        List<JourneyRow> rows = jdbcTemplate.query("""
                SELECT event.line_id, event.station_code, line.line_name
                FROM t_match_events event LEFT JOIN t_metro_lines line ON line.line_id = event.line_id
                WHERE event.id = ? LIMIT 1
                """, (row, ignored) -> new JourneyRow(row.getString(1), row.getString(2), row.getString(3)), base.matchId());
        Journey journey = rows.isEmpty() ? null : journey(rows.get(0).lineId(), rows.get(0).stationCode(), rows.get(0).lineName());
        return new Session(base.sessionId(), base.matchId(), base.rideEndsAt(), base.replyWindowEndsAt(),
                base.status(), journey, base.peerDisplayName(), base.peerAvatarKey());
    }

    private Journey journey(String lineId, String stationCode, String lineName) {
        if (lineId == null || lineId.isBlank() || lineName == null || jdbcTemplate == null) return null;
        List<StationRow> stations = jdbcTemplate.query("""
                SELECT station_code, station_name FROM t_metro_stations
                WHERE line_id = ? ORDER BY sequence_no
                """, (row, ignored) -> new StationRow(row.getString(1), row.getString(2)), lineId);
        // 当前摘要没有上下行方向；仅返回真实线路，避免默认首站和末站伪造旅程。
        return new Journey(lineId, lineName, MetroContracts.lineColor(lineName), null, null, null, 0, 0);
    }

    private record JourneyRow(String lineId, String stationCode, String lineName) { }
    private record StationRow(String code, String name) { }

    private String shortId(String value) { return value == null ? "none" : value.length() <= 8 ? value : value.substring(0, 8); }
}
