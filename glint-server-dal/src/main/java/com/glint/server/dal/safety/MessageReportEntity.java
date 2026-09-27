package com.glint.server.dal.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "t_message_reports")
public class MessageReportEntity {

    @Id
    private String id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "reporter_id", nullable = false)
    private String reporterId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected MessageReportEntity() {
    }

    private MessageReportEntity(String id, String messageId, String reporterId, String reason, Instant createdAt,
                                Instant expiresAt) {
        this.id = id;
        this.messageId = messageId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.status = "PENDING";
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static MessageReportEntity pending(String id, String messageId, String reporterId, String reason,
                                              Instant createdAt, Instant expiresAt) {
        return new MessageReportEntity(id, messageId, reporterId, reason, createdAt, expiresAt);
    }
}
