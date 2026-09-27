package com.glint.server.biz.presentation;

import com.glint.server.common.presentation.PresentationContracts.HomeSummary;
import com.glint.server.common.presentation.PresentationContracts.ProfileDashboard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
/** 为首页和“我的”聚合最小展示数据，避免客户端下载全量社交记录计数。 */
public class PresentationService {

    private final JdbcTemplate jdbcTemplate;
    private final AchievementService achievementService;

    public PresentationService(JdbcTemplate jdbcTemplate, AchievementService achievementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.achievementService = achievementService;
    }

    public HomeSummary homeSummary(String userId) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        int encounters = count("""
                SELECT COUNT(*) FROM t_match_events
                WHERE (first_user_id = ? OR second_user_id = ?)
                  AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) = ?
                """, userId, userId, today);
        int interested = count("""
                SELECT COUNT(*) FROM t_match_events event
                JOIN t_match_unlock_states state ON state.match_event_id = event.id
                WHERE ((event.first_user_id = ? AND state.first_user_interested)
                    OR (event.second_user_id = ? AND state.second_user_interested))
                  AND DATE(CONVERT_TZ(event.occurred_at, '+00:00', '+08:00')) = ?
                """, userId, userId, today);
        int mutual = count("""
                SELECT COUNT(*) FROM t_match_events event
                JOIN t_match_unlock_states state ON state.match_event_id = event.id
                WHERE (event.first_user_id = ? OR event.second_user_id = ?)
                  AND state.first_user_interested AND state.second_user_interested
                  AND DATE(CONVERT_TZ(event.occurred_at, '+00:00', '+08:00')) = ?
                """, userId, userId, today);
        int chats = count("""
                SELECT COUNT(DISTINCT session.id) FROM t_chat_sessions session
                JOIN t_match_events event ON event.id = session.match_event_id
                WHERE (event.first_user_id = ? OR event.second_user_id = ?)
                  AND DATE(CONVERT_TZ(event.occurred_at, '+00:00', '+08:00')) = ?
                """, userId, userId, today);
        return new HomeSummary(encounters, interested, chats, mutual, 0);
    }

    public ProfileDashboard profileDashboard(String userId) {
        Identity identity = jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(nickname, ''), '匿名同路人'), COALESCE(avatar_key, 'aurora')
                FROM t_profiles WHERE user_id = ?
                """, row -> row.next() ? new Identity(row.getString(1), row.getString(2)) : null, userId);
        if (identity == null) identity = new Identity("匿名同路人", "aurora");
        int encounters = count("SELECT COUNT(*) FROM t_match_events WHERE first_user_id = ? OR second_user_id = ?",
                userId, userId);
        int unlocked = count("""
                SELECT COUNT(*) FROM t_match_events event JOIN t_match_unlock_states state ON state.match_event_id = event.id
                WHERE (event.first_user_id = ? OR event.second_user_id = ?) AND state.unlock_level >= 2
                """, userId, userId);
        int posts = count("SELECT COUNT(*) FROM t_board_posts WHERE author_id = ?", userId);
        var achievements = achievementService.achievements(userId);
        int unlockedAchievements = (int) achievements.stream().filter(item -> item.unlocked()).count();
        String title = unlocked >= 5 ? "通勤达人" : encounters > 0 ? "城市漫游者" : "新鲜乘客";
        return new ProfileDashboard(identity.displayName(), identity.avatarKey(), title,
                encounters, unlocked, posts, unlockedAchievements, achievements.size());
    }

    int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private record Identity(String displayName, String avatarKey) { }
}
