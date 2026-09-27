package com.glint.server.biz.board;

import com.glint.server.common.board.BoardContracts.Post;
import com.glint.server.common.board.BoardContracts.Comment;
import com.glint.server.common.board.BoardContracts.PostInteraction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
/** 匿名留言板持久化服务；日志不记录留言正文。 */
public class BoardService {
    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final JdbcTemplate jdbcTemplate;

    public BoardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Post> posts(String lineCode, String viewerId) {
        List<Post> posts = jdbcTemplate.query("""
                SELECT post.id, post.line_code, post.content, post.created_at, post.author_id,
                       (SELECT COUNT(*) FROM t_board_post_likes liked WHERE liked.post_id = post.id),
                       (SELECT COUNT(*) FROM t_board_comments comment WHERE comment.post_id = post.id),
                       EXISTS(SELECT 1 FROM t_board_post_likes mine WHERE mine.post_id = post.id AND mine.user_id = ?)
                FROM t_board_posts post WHERE post.line_code = ? ORDER BY post.created_at DESC LIMIT 100
                """, (row, ignored) -> new Post(row.getString(1), row.getString(2), row.getString(3),
                row.getTimestamp(4).toInstant(), alias(row.getString(5)), avatarKey(row.getString(5)),
                row.getInt(6), row.getInt(7), row.getBoolean(8)), viewerId, lineCode);
        log.info("board_posts_loaded lineCode={}, count={}", lineCode, posts.size());
        return posts;
    }

    /** 保留内部调用兼容；无查看者时不标记本人点赞。 */
    public List<Post> posts(String lineCode) { return posts(lineCode, ""); }

    public Post create(String userId, String lineCode, String content) {
        verifyLineAccess(userId, lineCode);
        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_board_posts (id, author_id, line_code, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, userId, lineCode, content, createdAt);
        log.info("board_post_persisted userId={}, postId={}, contentLength={}", shortId(userId), shortId(id), content.length());
        return new Post(id, lineCode, content, createdAt, alias(userId), avatarKey(userId), 0, 0, false);
    }

    public PostInteraction setLiked(String userId, String postId, boolean liked) {
        if (liked) {
            jdbcTemplate.update("INSERT IGNORE INTO t_board_post_likes (post_id, user_id) VALUES (?, ?)", postId, userId);
        } else {
            jdbcTemplate.update("DELETE FROM t_board_post_likes WHERE post_id = ? AND user_id = ?", postId, userId);
        }
        return interaction(postId, userId);
    }

    public List<Comment> comments(String postId) {
        return jdbcTemplate.query("""
                SELECT id, post_id, content, created_at, author_id FROM t_board_comments
                WHERE post_id = ? ORDER BY created_at ASC LIMIT 200
                """, (row, ignored) -> new Comment(row.getString(1), row.getString(2), row.getString(3),
                row.getTimestamp(4).toInstant(), alias(row.getString(5)), avatarKey(row.getString(5))), postId);
    }

    public Comment comment(String userId, String postId, String content) {
        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_board_comments (id, post_id, author_id, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, postId, userId, content, createdAt);
        log.info("board_comment_persisted userId={}, postId={}, commentId={}, contentLength={}",
                shortId(userId), shortId(postId), shortId(id), content.length());
        return new Comment(id, postId, content, createdAt, alias(userId), avatarKey(userId));
    }

    private PostInteraction interaction(String postId, String userId) {
        return jdbcTemplate.queryForObject("""
                SELECT (SELECT COUNT(*) FROM t_board_post_likes WHERE post_id = ?),
                       (SELECT COUNT(*) FROM t_board_comments WHERE post_id = ?),
                       EXISTS(SELECT 1 FROM t_board_post_likes WHERE post_id = ? AND user_id = ?)
                """, (row, ignored) -> new PostInteraction(postId, row.getInt(1), row.getInt(2), row.getBoolean(3)),
                postId, postId, postId, userId);
    }

    public int onlineCount(String lineCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT user_id) FROM t_commute_station_summaries
                WHERE expires_at > CURRENT_TIMESTAMP AND status_summary = 'searching' AND line_id = ?
                """, Integer.class, lineCode);
        int result = count == null ? 0 : count;
        log.info("board_online_count_calculated lineCode={}, count={}", lineCode, result);
        return result;
    }

    private void verifyLineAccess(String userId, String lineCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT 1 FROM t_commute_station_summaries
                    WHERE user_id = ? AND line_id = ? AND expires_at > CURRENT_TIMESTAMP
                    UNION ALL
                    SELECT 1 FROM t_commute_preferences
                    WHERE user_id = ? AND JSON_CONTAINS(preferred_line_ids, JSON_QUOTE(?))
                ) allowed_line
                """, Integer.class, userId, lineCode, userId, lineCode);
        if (count == null || count == 0) throw new IllegalArgumentException("只能向当前或常用通勤线路发布留言");
    }

    private String alias(String userId) {
        String[] aliases = {"奶油小方", "韭菜盒子侠", "寻找AirPods", "薄荷乘客", "晚风同路人"};
        return aliases[Math.floorMod(userId == null ? 0 : userId.hashCode(), aliases.length)];
    }

    private String avatarKey(String userId) {
        String[] avatars = {"aurora", "mint", "sky"};
        return avatars[Math.floorMod(userId == null ? 0 : userId.hashCode(), avatars.length)];
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
