package com.glint.server.web;

import com.glint.server.biz.chat.ChatService;
import com.glint.server.biz.chat.MessageCipher;
import com.glint.server.biz.safety.ExpiredDataCleanupService;
import com.glint.server.biz.safety.SafetyService;
import com.glint.server.dal.chat.ChatMessageRepository;
import com.glint.server.dal.chat.ChatSessionEntity;
import com.glint.server.dal.chat.ChatSessionRepository;
import com.glint.server.dal.safety.MessageReportRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import com.glint.server.biz.auth.ResourceAccessDeniedException;
import static org.mockito.Mockito.when;

class PersistenceRegressionTests {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String DELETE_REPORTS_FOR_EXPIRED_MESSAGES = """
            DELETE report FROM t_message_reports report
            INNER JOIN t_chat_messages message ON report.message_id = message.id
            WHERE message.expires_at <= CURRENT_TIMESTAMP
            """;

    @Test
    void deletesEveryReportForExpiredMessageBeforeDeletingTheMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        new ExpiredDataCleanupService(jdbcTemplate).deleteExpiredRecords();

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(DELETE_REPORTS_FOR_EXPIRED_MESSAGES);
        order.verify(jdbcTemplate).update("DELETE FROM t_chat_messages WHERE expires_at <= CURRENT_TIMESTAMP");
    }

    @Test
    void assignsDifferentIdsToTwoMessagesInTheSameSession() {
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        when(sessions.findById("session-1")).thenReturn(Optional.of(
                ChatSessionEntity.create(
                        "session-1",
                        "match-1",
                        Instant.parse("2099-08-10T13:00:00Z"),
                        Instant.parse("2099-08-11T13:00:00Z"),
                        "RIDING"
                )
        ));
        ChatService chatService = new ChatService(
                new MessageCipher(TEST_KEY),
                sessions,
                mock(ChatMessageRepository.class)
        );

        var first = chatService.send("session-1", "user-1", "first");
        var second = chatService.send("session-1", "user-1", "second");

        assertThat(first.messageId()).isNotEqualTo(second.messageId());
    }

    @Test
    void assignsDifferentIdsToTwoReportsForTheSameMessage() {
        SafetyService safetyService = new SafetyService(mock(MessageReportRepository.class));

        var first = safetyService.report("message-1", "user-1", "abuse");
        var second = safetyService.report("message-1", "user-2", "spam");

        assertThat(first.reportId()).isNotEqualTo(second.reportId());
    }

    @Test
    void allowsMatchParticipantToLoadMessages() {
        ChatService chatService = chatServiceWithParticipantCount(1);

        assertThat(chatService.messages("session-virtual", "user-real")).isEmpty();
    }

    @Test
    void rejectsNonParticipantFromLoadingMessages() {
        ChatService chatService = chatServiceWithParticipantCount(0);

        assertThatThrownBy(() -> chatService.messages("session-virtual", "user-other"))
                .isInstanceOf(ResourceAccessDeniedException.class)
                .hasMessage("无权访问该聊天会话");
    }

    @Test
    void rejectsNonParticipantFromCreatingChatSession() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("match-1"), eq("intruder"), eq("intruder")))
                .thenReturn(0);
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ChatService chatService = new ChatService(new MessageCipher(TEST_KEY), sessions, messages, jdbcTemplate);

        assertThatThrownBy(() -> chatService.createSession("match-1", "intruder"))
                .isInstanceOf(ResourceAccessDeniedException.class)
                .hasMessage("匹配不存在、已过期或无权创建聊天会话");
        verifyNoInteractions(sessions, messages);
    }

    @Test
    void rejectsNonParticipantFromReportingMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("message-1"), eq("intruder"), eq("intruder")))
                .thenReturn(0);
        MessageReportRepository reports = mock(MessageReportRepository.class);

        assertThatThrownBy(() -> new SafetyService(reports, jdbcTemplate)
                .report("message-1", "intruder", "abuse"))
                .isInstanceOf(ResourceAccessDeniedException.class)
                .hasMessage("消息不存在或无权举报");
        verifyNoInteractions(reports);
    }

    private ChatService chatServiceWithParticipantCount(int count) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("session-virtual"), anyString(), anyString()))
                .thenReturn(count);
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        when(sessions.findById("session-virtual")).thenReturn(Optional.of(
                ChatSessionEntity.create(
                        "session-virtual",
                        "match-virtual",
                        Instant.parse("2099-08-10T13:00:00Z"),
                        Instant.parse("2099-08-11T13:00:00Z"),
                        "RIDING"
                )
        ));
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        when(messages.findBySessionIdOrderByCreatedAtAsc("session-virtual")).thenReturn(List.of());
        return new ChatService(new MessageCipher(TEST_KEY), sessions, messages, jdbcTemplate);
    }
}
