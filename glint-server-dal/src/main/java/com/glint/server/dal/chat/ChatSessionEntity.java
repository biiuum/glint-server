package com.glint.server.dal.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "t_chat_sessions")
public class ChatSessionEntity {

    @Id
    private String id;

    @Column(name = "match_event_id")
    private String matchEventId;

    @Column(name = "ride_ends_at")
    private Instant rideEndsAt;

    @Column(name = "reply_window_ends_at")
    private Instant replyWindowEndsAt;

    @Column(nullable = false)
    private String status;

    protected ChatSessionEntity() {
    }

    private ChatSessionEntity(String id, String matchEventId, Instant rideEndsAt, Instant replyWindowEndsAt, String status) {
        this.id = id;
        this.matchEventId = matchEventId;
        this.rideEndsAt = rideEndsAt;
        this.replyWindowEndsAt = replyWindowEndsAt;
        this.status = status;
    }

    public static ChatSessionEntity create(String id, String matchEventId, Instant rideEndsAt, Instant replyWindowEndsAt, String status) {
        return new ChatSessionEntity(id, matchEventId, rideEndsAt, replyWindowEndsAt, status);
    }

    public String getId() { return id; }
    public String getMatchEventId() { return matchEventId; }
    public Instant getRideEndsAt() { return rideEndsAt; }
    public Instant getReplyWindowEndsAt() { return replyWindowEndsAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
