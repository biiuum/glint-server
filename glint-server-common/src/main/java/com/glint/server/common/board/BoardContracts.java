package com.glint.server.common.board;

import java.time.Instant;
import java.util.List;

public final class BoardContracts {

    private BoardContracts() {
    }

    public record Post(String postId, String lineCode, String content, Instant createdAt,
                       String authorAlias, String avatarKey, int likeCount, int commentCount,
                       boolean likedByMe) {
        public Post(String postId, String lineCode, String content, Instant createdAt) {
            this(postId, lineCode, content, createdAt, "匿名同路人", "aurora", 0, 0, false);
        }
    }

    public record Comment(String commentId, String postId, String content, Instant createdAt,
                          String authorAlias, String avatarKey) {
    }

    public record PostInteraction(String postId, int likeCount, int commentCount, boolean likedByMe) {
    }
}
