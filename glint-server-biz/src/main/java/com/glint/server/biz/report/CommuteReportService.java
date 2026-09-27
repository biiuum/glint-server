package com.glint.server.biz.report;

import com.glint.server.common.report.ReportContracts.CommuteReport;
import com.glint.server.common.report.ReportContracts.DailyTrend;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
/** 聚合用户当天匿名通勤数据的报告服务。 */
public class CommuteReportService {
    private static final Logger log = LoggerFactory.getLogger(CommuteReportService.class);

    private final JdbcTemplate jdbcTemplate;

    public CommuteReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommuteReport today(String userId) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Integer encounters = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT CASE WHEN first_user_id = ? THEN second_user_id ELSE first_user_id END)
                FROM t_match_events WHERE (first_user_id = ? OR second_user_id = ?)
                AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) = ?
                """, Integer.class, userId, userId, userId, date);
        int encounterCount = encounters == null ? 0 : encounters;
        int sameRouteCount = count("""
                SELECT COUNT(DISTINCT CASE WHEN first_user_id = ? THEN second_user_id ELSE first_user_id END)
                FROM t_match_events WHERE (first_user_id = ? OR second_user_id = ?)
                  AND line_id IS NOT NULL
                  AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) = ?
                """, userId, userId, userId, date);
        String reportId = jdbcTemplate.query("SELECT id FROM t_commute_reports WHERE user_id = ? AND report_date = ?",
                row -> row.next() ? row.getString(1) : null, userId, date);
        if (reportId == null) reportId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO t_commute_reports (id, user_id, report_date, encounter_count, same_route_count)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE encounter_count = VALUES(encounter_count), same_route_count = VALUES(same_route_count)
                """, reportId, userId, date, encounterCount, sameRouteCount);
        int sharedInterests = count("""
                SELECT COUNT(DISTINCT CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END)
                FROM t_match_events event
                LEFT JOIN t_profiles me ON me.user_id = ?
                LEFT JOIN t_profiles other ON other.user_id = CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END
                WHERE (event.first_user_id = ? OR event.second_user_id = ?)
                  AND DATE(CONVERT_TZ(event.occurred_at, '+00:00', '+08:00')) = ?
                  AND JSON_OVERLAPS(COALESCE(me.interest_tags, JSON_ARRAY()), COALESCE(other.interest_tags, JSON_ARRAY()))
                """, userId, userId, userId, userId, userId, date);
        String sharedInterestLabel = topSharedInterestLabel(userId, date);
        int averageMinutes = averageMinutes(userId, date);
        int earlierMinutes = earlierMinutes(userId, date);
        List<DailyTrend> trend = weeklyTrend(userId, date);
        int currentWeek = trend.stream().mapToInt(DailyTrend::encounterCount).sum();
        int previousWeek = count("""
                SELECT COUNT(*) FROM t_match_events WHERE (first_user_id = ? OR second_user_id = ?)
                  AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) BETWEEN ? AND ?
                """, userId, userId, date.minusDays(13), date.minusDays(7));
        int weekOverWeek = previousWeek == 0 ? (currentWeek > 0 ? 100 : 0)
                : (int) Math.round((currentWeek - previousWeek) * 100.0 / previousWeek);
        log.info("commute_report_generated userId={}, reportId={}, encounterCount={}", shortId(userId), shortId(reportId), encounterCount);
        return new CommuteReport(reportId, date, encounterCount, sameRouteCount, sharedInterests,
                sharedInterestLabel, earlierMinutes, averageMinutes, weekOverWeek, trend);
    }

    private List<DailyTrend> weeklyTrend(String userId, LocalDate endDate) {
        List<DailyTrend> values = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = endDate.minusDays(offset);
            int value = count("""
                    SELECT COUNT(DISTINCT CASE WHEN first_user_id = ? THEN second_user_id ELSE first_user_id END)
                    FROM t_match_events WHERE (first_user_id = ? OR second_user_id = ?)
                      AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) = ?
                    """, userId, userId, userId, date);
            values.add(new DailyTrend(date, value));
        }
        return List.copyOf(values);
    }

    private int averageMinutes(String userId, LocalDate date) {
        List<Timestamp> bounds = jdbcTemplate.query("""
                SELECT MIN(occurred_at), MAX(occurred_at) FROM t_commute_station_summaries
                WHERE user_id = ? AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) = ?
                """, row -> {
            if (!row.next()) return List.of();
            Timestamp first = row.getTimestamp(1);
            Timestamp last = row.getTimestamp(2);
            return first == null || last == null ? List.of() : List.of(first, last);
        }, userId, date);
        if (bounds.size() < 2) return 0;
        return (int) Math.max(0, ChronoUnit.MINUTES.between(bounds.get(0).toInstant(), bounds.get(1).toInstant()));
    }

    private int earlierMinutes(String userId, LocalDate date) {
        LocalTime today = firstTime(userId, date);
        LocalTime yesterday = firstTime(userId, date.minusDays(1));
        if (today == null || yesterday == null) return 0;
        return (int) ChronoUnit.MINUTES.between(today, yesterday);
    }

    private LocalTime firstTime(String userId, LocalDate date) {
        Timestamp value = jdbcTemplate.query("""
                SELECT MIN(occurred_at) FROM t_commute_station_summaries
                WHERE user_id = ? AND DATE(CONVERT_TZ(occurred_at, '+00:00', '+08:00')) = ?
                """, row -> row.next() ? row.getTimestamp(1) : null, userId, date);
        if (value == null) return null;
        return LocalDateTime.ofInstant(value.toInstant(), ZoneId.of("Asia/Shanghai")).toLocalTime();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private String topSharedInterestLabel(String userId, LocalDate date) {
        String tag = jdbcTemplate.query("""
                SELECT mine_tag.tag
                FROM t_profiles me
                JOIN JSON_TABLE(COALESCE(me.interest_tags, JSON_ARRAY()), '$[*]'
                    COLUMNS(tag VARCHAR(128) PATH '$')) mine_tag
                JOIN t_match_events event ON event.first_user_id = me.user_id OR event.second_user_id = me.user_id
                JOIN t_profiles other ON other.user_id = CASE
                    WHEN event.first_user_id = me.user_id THEN event.second_user_id ELSE event.first_user_id END
                WHERE me.user_id = ?
                  AND DATE(CONVERT_TZ(event.occurred_at, '+00:00', '+08:00')) = ?
                  AND JSON_CONTAINS(COALESCE(other.interest_tags, JSON_ARRAY()), JSON_QUOTE(mine_tag.tag))
                GROUP BY mine_tag.tag
                ORDER BY COUNT(DISTINCT other.user_id) DESC, mine_tag.tag ASC
                LIMIT 1
                """, row -> row.next() ? row.getString(1) : null, userId, date);
        return tag == null || tag.isBlank() ? null : "和你一样喜欢" + tag;
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
