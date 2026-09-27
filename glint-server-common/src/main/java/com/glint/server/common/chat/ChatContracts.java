package com.glint.server.common.chat;

import com.glint.server.common.matching.MatchingContracts.Journey;
import java.time.Instant;

public final class ChatContracts {

    private ChatContracts() {
    }

    public record Session(String sessionId, String matchId, Instant rideEndsAt, Instant replyWindowEndsAt,
                          String status, Journey journey, String peerDisplayName, String peerAvatarKey) {
        public Session(String sessionId, String matchId, Instant rideEndsAt, Instant replyWindowEndsAt,
                       String status) {
            this(sessionId, matchId, rideEndsAt, replyWindowEndsAt, status, null, "神秘乘客", "aurora");
        }
    }

    public record Message(String messageId, String sessionId, String senderId, String text, Instant createdAt,
                          String bottleStatus) {
    }
}
