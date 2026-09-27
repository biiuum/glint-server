package com.glint.server.biz.presentation;

import com.glint.server.common.presentation.PresentationContracts.Achievement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
/** 从既有业务事实计算成就；只持久化首次解锁时间。 */
public class AchievementService {

    private final JdbcTemplate jdbcTemplate;

    public AchievementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<Achievement> achievements(String userId) {
        int encounters = count("SELECT COUNT(*) FROM t_match_events WHERE first_user_id = ? OR second_user_id = ?", userId, userId);
        int unlocked = count("""
                SELECT COUNT(*) FROM t_match_events event JOIN t_match_unlock_states state ON state.match_event_id = event.id
                WHERE (event.first_user_id = ? OR event.second_user_id = ?) AND state.unlock_level >= 2
                """, userId, userId);
        int earlyDays = longestEarlyStreak(userId);
        int chats = count("""
                SELECT COUNT(DISTINCT session.id) FROM t_chat_sessions session
                JOIN t_match_events event ON event.id = session.match_event_id
                WHERE event.first_user_id = ? OR event.second_user_id = ?
                """, userId, userId);
        int posts = count("SELECT COUNT(*) FROM t_board_posts WHERE author_id = ?", userId);
        int lateTrips = count("""
                SELECT COUNT(*) FROM t_commute_station_summaries WHERE user_id = ?
                  AND HOUR(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) >= 22
                """, userId);

        List<Definition> definitions = List.of(
                new Definition("FIRST_MEET", "初次相遇", "achievement-first-meet", "解锁 1 位同路人", unlocked, 1),
                new Definition("COMMUTE_EXPERT", "通勤达人", "achievement-commute-expert", "解锁 5 位同路人", unlocked, 5),
                new Definition("EARLY_BIRD", "早鸟", "achievement-early-bird", "连续早起打卡 7 天", earlyDays, 7),
                new Definition("TOPIC_MASTER", "话题王", "achievement-topic-master", "发布 10 条留言", posts, 10),
                new Definition("CHAT_COMPANION", "同路畅聊", "achievement-chat-companion", "开启 10 次聊天", chats, 10),
                new Definition("NIGHT_RIDER", "夜归人", "achievement-night-rider", "累计夜间出行 20 次", lateTrips, 20),
                new Definition("SOCIAL_BUTTERFLY", "社交蝴蝶", "achievement-social-butterfly", "累计擦肩 100 人", encounters, 100)
        );
        List<Achievement> result = new ArrayList<>();
        for (Definition definition : definitions) {
            boolean isUnlocked = definition.current() >= definition.target();
            if (isUnlocked) {
                jdbcTemplate.update("""
                        INSERT IGNORE INTO t_user_achievements (user_id, achievement_code, earned_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        """, userId, definition.code());
            }
            Instant earnedAt = jdbcTemplate.query("""
                    SELECT earned_at FROM t_user_achievements WHERE user_id = ? AND achievement_code = ?
                    """, row -> row.next() ? row.getTimestamp(1).toInstant() : null, userId, definition.code());
            result.add(new Achievement(definition.code(), definition.name(), definition.iconKey(), definition.condition(),
                    definition.current(), definition.target(), isUnlocked, earnedAt));
        }
        return result;
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private int longestEarlyStreak(String userId) {
        List<LocalDate> dates = jdbcTemplate.query("""
                SELECT DISTINCT DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) AS commute_date
                FROM t_commute_station_summaries WHERE user_id = ?
                  AND HOUR(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) < 8
                ORDER BY commute_date ASC
                """, (row, ignored) -> row.getDate(1).toLocalDate(), userId);
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            current = previous != null && previous.plusDays(1).equals(date) ? current + 1 : 1;
            longest = Math.max(longest, current);
            previous = date;
        }
        return longest;
    }

    private record Definition(String code, String name, String iconKey, String condition,
                              int current, int target) {
    }
}
