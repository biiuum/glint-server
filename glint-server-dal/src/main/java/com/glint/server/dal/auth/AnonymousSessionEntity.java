package com.glint.server.dal.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "t_anonymous_sessions")
public class AnonymousSessionEntity {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "token_digest", nullable = false, unique = true)
    private String tokenDigest;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AnonymousSessionEntity() {
    }

    private AnonymousSessionEntity(String id, String userId, String tokenDigest, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenDigest = tokenDigest;
        this.expiresAt = expiresAt;
    }

    public static AnonymousSessionEntity active(String id, String userId, String tokenDigest, Instant expiresAt) {
        return new AnonymousSessionEntity(id, userId, tokenDigest, expiresAt);
    }

    public String getUserId() {
        return userId;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
