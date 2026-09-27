package com.glint.server.web;

import com.glint.server.biz.chat.ChatService;
import com.glint.server.biz.matching.MatchingCandidateTtlPolicy;
import com.glint.server.biz.safety.ExpiredDataCleanupService;
import com.glint.server.biz.safety.SafetyService;
import com.glint.server.dal.chat.ChatMessageEntity;
import com.glint.server.dal.chat.ChatMessageRepository;
import com.glint.server.dal.safety.MessageReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=false",
        "oss.access-key-id=",
        "glint.message-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class PersistenceIntegrationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("glint")
            .withUsername("glint")
            .withPassword("glint")
            .withInitScript("db/glint_schema.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MessageReportRepository messageReportRepository;

    @Autowired
    private MatchingCandidateTtlPolicy matchingCandidateTtlPolicy;

    @Autowired
    private ExpiredDataCleanupService expiredDataCleanupService;

    @Autowired
    private SafetyService safetyService;

    @Test
    void initializationScriptCreatesCoreTables() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN ('t_users', 't_profiles', 't_anonymous_sessions', 't_chat_messages')
                """, Integer.class);

        assertThat(tables).isEqualTo(4);
    }

    @Test
    void persistsEncryptedMessageAndItsReport() {
        insertMatch("match-demo", "user-1", "user-2");
        var session = chatService.createSession("match-demo", "user-1");
        var firstMessage = chatService.send(session.sessionId(), "user-1", "不能落库明文");
        var secondMessage = chatService.send(session.sessionId(), "user-1", "第二条消息");
        ChatMessageEntity stored = chatMessageRepository.findById(firstMessage.messageId()).orElseThrow();

        assertThat(stored.getCiphertext()).isNotEqualTo("不能落库明文");
        assertThat(stored.getNonce()).isNotBlank();
        assertThat(stored.getKeyVersion()).isEqualTo("v1");
        assertThat(firstMessage.messageId()).isNotEqualTo(secondMessage.messageId());
        assertThat(chatMessageRepository.findById(secondMessage.messageId())).isPresent();

        var firstReport = safetyService.report(firstMessage.messageId(), "user-1", "abuse");
        var secondReport = safetyService.report(firstMessage.messageId(), "user-2", "spam");

        assertThat(firstReport.reportId()).isNotEqualTo(secondReport.reportId());
        assertThat(messageReportRepository.findByMessageId(firstMessage.messageId())).hasSize(2);
    }

    @Test
    void writesMatchingCandidateWithCappedRedisTtl() {
        Duration appliedTtl = matchingCandidateTtlPolicy.cacheCandidate(
                "match-cache-test", "candidate-payload", Duration.ofHours(48)
        );

        assertThat(appliedTtl).isEqualTo(Duration.ofHours(24));
        assertThat(matchingCandidateTtlPolicy.remainingTtl("match-cache-test").toSeconds())
                .isBetween(1L, Duration.ofHours(24).toSeconds());
    }

    @Test
    void deletesExpiredMessagesAndReports() {
        insertMatch("match-expired", "user-1", "user-2");
        var session = chatService.createSession("match-expired", "user-1");
        ChatMessageEntity expired = ChatMessageEntity.encrypted(
                "expired-message", session.sessionId(), "user-1", "ciphertext", "nonce", "v1",
                Instant.now().minus(Duration.ofDays(8)), Instant.now().minusSeconds(1)
        );
        chatMessageRepository.save(expired);
        safetyService.report(expired.getId(), "user-2", "abuse");

        expiredDataCleanupService.deleteExpiredRecords();

        assertThat(chatMessageRepository.existsById(expired.getId())).isFalse();
        assertThat(messageReportRepository.findByMessageId(expired.getId())).isEmpty();
    }

    private void insertMatch(String matchId, String firstUserId, String secondUserId) {
        jdbcTemplate.update("""
                INSERT INTO t_match_events
                    (id, first_user_id, second_user_id, station_code, occurred_at, expires_at, pair_key)
                VALUES (?, ?, ?, 'integration-station', ?, ?, ?)
                """, matchId, firstUserId, secondUserId, Instant.now().minusSeconds(5),
                Instant.now().plus(Duration.ofHours(1)), firstUserId + ":" + secondUserId + ":" + matchId);
    }
}
