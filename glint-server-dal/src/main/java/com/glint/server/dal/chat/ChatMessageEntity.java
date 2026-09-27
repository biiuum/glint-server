package com.glint.server.dal.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "t_chat_messages")
public class ChatMessageEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String ciphertext;

    @Column(nullable = false)
    private String nonce;

    @Column(name = "key_version", nullable = false)
    private String keyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ChatMessageEntity() {
    }

    private ChatMessageEntity(String id, String sessionId, String senderId, String ciphertext, String nonce,
                              String keyVersion, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.senderId = senderId;
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.keyVersion = keyVersion;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static ChatMessageEntity encrypted(String id, String sessionId, String senderId, String ciphertext,
                                              String nonce, String keyVersion, Instant createdAt, Instant expiresAt) {
        return new ChatMessageEntity(id, sessionId, senderId, ciphertext, nonce, keyVersion, createdAt, expiresAt);
    }

    public String getId() {
        return id;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public String getNonce() {
        return nonce;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public String getSessionId() { return sessionId; }
    public String getSenderId() { return senderId; }
    public Instant getCreatedAt() { return createdAt; }
}
