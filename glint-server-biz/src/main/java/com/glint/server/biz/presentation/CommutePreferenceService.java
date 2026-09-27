package com.glint.server.biz.presentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glint.server.common.presentation.PresentationContracts.CommutePreferences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
/** 持久化用户显式选择的站点级偏好，不接收定位坐标。 */
public class CommutePreferenceService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CommutePreferenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public CommutePreferences current(String userId) {
        CommutePreferences found = jdbcTemplate.query("""
                SELECT start_time, end_time, preferred_line_ids, auto_match, notifications_enabled, stealth,
                       favorite_station_code, favorite_station_name
                FROM t_commute_preferences WHERE user_id = ?
                """, row -> row.next() ? new CommutePreferences(row.getString(1), row.getString(2), lines(row.getString(3)),
                row.getBoolean(4), row.getBoolean(5), row.getBoolean(6), row.getString(7), row.getString(8)) : null, userId);
        return found == null ? defaults() : found;
    }

    @Transactional
    public CommutePreferences save(String userId, CommutePreferences preference) {
        LocalTime start = LocalTime.parse(preference.startTime(), TIME);
        LocalTime end = LocalTime.parse(preference.endTime(), TIME);
        if (!end.isAfter(start)) throw new IllegalArgumentException("通勤结束时间必须晚于开始时间");
        List<String> lineIds = preference.preferredLineIds() == null ? List.of() : preference.preferredLineIds().stream()
                .filter(value -> value != null && !value.isBlank()).distinct().limit(20).toList();
        jdbcTemplate.update("""
                INSERT INTO t_commute_preferences
                (user_id, start_time, end_time, preferred_line_ids, auto_match, notifications_enabled, stealth,
                 favorite_station_code, favorite_station_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE start_time = VALUES(start_time), end_time = VALUES(end_time),
                  preferred_line_ids = VALUES(preferred_line_ids), auto_match = VALUES(auto_match),
                  notifications_enabled = VALUES(notifications_enabled), stealth = VALUES(stealth),
                  favorite_station_code = VALUES(favorite_station_code), favorite_station_name = VALUES(favorite_station_name)
                """, userId, TIME.format(start), TIME.format(end), json(lineIds), preference.autoMatch(),
                preference.notificationsEnabled(), preference.stealth(), blank(preference.favoriteStationCode()),
                blank(preference.favoriteStationName()));
        return current(userId);
    }

    private CommutePreferences defaults() {
        return new CommutePreferences("07:30", "09:30", List.of(), true, true, false, null, null);
    }

    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (Exception exception) { throw new IllegalArgumentException("常用线路格式无效", exception); }
    }

    private List<String> lines(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() { }); }
        catch (Exception exception) { return List.of(); }
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
