package com.glint.server.biz.matching;

import com.glint.server.biz.auth.ResourceAccessDeniedException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glint.server.common.matching.MatchingContracts.Candidate;
import com.glint.server.common.matching.MatchingContracts.Journey;
import com.glint.server.common.matching.MatchingContracts.Topic;
import com.glint.server.common.matching.MatchingContracts.UnlockProgress;
import com.glint.server.common.metro.MetroContracts;
import com.glint.server.dal.matching.MatchingLinePresenceCache;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
/** 基于站点、时间和方向派生匿名匹配及解锁状态的服务。 */
public class MatchingService {

    /** 系统虚拟路人固定身份；不写入 t_users 表，t_match_events 无用户外键。 */
    static final String VIRTUAL_USER_ID = "00000000-0000-4000-a000-ffffffffffff";

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);
    private final MatchingCandidateTtlPolicy matchingCandidateTtlPolicy;
    private final MatchingLinePresenceCache matchingLinePresenceCache;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, String> lastTopicByUser = new ConcurrentHashMap<>();

    public MatchingService(MatchingCandidateTtlPolicy matchingCandidateTtlPolicy,
                           MatchingLinePresenceCache matchingLinePresenceCache,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper) {
        this.matchingCandidateTtlPolicy = matchingCandidateTtlPolicy;
        this.matchingLinePresenceCache = matchingLinePresenceCache;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Candidate> candidates(String userId) {
        List<Candidate> candidates = jdbcTemplate.query("""
                SELECT event.id, CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END,
                       COALESCE(profile.avatar_key, 'aurora'), COALESCE(profile.interest_tags, JSON_ARRAY()),
                       state.unlock_level, state.first_user_interested, state.second_user_interested,
                       state.first_user_unlock_consent, state.second_user_unlock_consent, state.real_info_exchange_confirmed,
                       (SELECT id FROM t_chat_sessions WHERE match_event_id = event.id LIMIT 1),
                       event.first_user_id, event.second_user_id, event.station_code, event.line_id
                FROM t_match_events event JOIN t_match_unlock_states state ON state.match_event_id = event.id
                LEFT JOIN t_profiles profile ON profile.user_id = CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END
                WHERE (event.first_user_id = ? OR event.second_user_id = ?) AND event.expires_at > CURRENT_TIMESTAMP
                ORDER BY event.occurred_at DESC
                """, (row, ignored) -> mapCandidate(
                row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                row.getInt(5), encounterCount(row.getString(1)), row.getBoolean(6), row.getBoolean(7),
                row.getBoolean(8), row.getBoolean(9), row.getBoolean(10), row.getString(11),
                row.getString(12), row.getString(13), row.getString(14), row.getString(15), userId), userId, userId, userId, userId);
        candidates.forEach(candidate -> matchingCandidateTtlPolicy.cacheCandidate(candidate.matchId(), candidate.profileId(), Duration.ofHours(24)));
        log.info("matching_candidates_loaded userId={}, count={}", shortId(userId), candidates.size());
        return candidates;
    }

    @Transactional
    public void createMatchesForSummary(String userId, String cityCode, String lineId, String stationCode,
                                        Instant occurredAt, double directionConfidence) {
        if (cityCode != null && lineId != null) {
            createLineMatches(userId, cityCode, lineId, stationCode, occurredAt, directionConfidence);
            return;
        }
        if (directionConfidence < MatchingDirectionPolicy.HIGH_CONFIDENCE) {
            log.info("matching_skipped_low_confidence userId={}, directionConfidence={}", shortId(userId), directionConfidence);
            return;
        }
        if (!MatchingLinePresenceCache.isRealStation(stationCode)) {
            log.info("matching_skipped_no_station userId={}", shortId(userId));
            return;
        }
        Timestamp[] window = matchWindow(occurredAt);
        List<String> peers = jdbcTemplate.queryForList("""
                SELECT DISTINCT user_id FROM t_commute_station_summaries
                WHERE user_id <> ? AND station_code = ? AND direction_confidence >= ?
                  AND occurred_at BETWEEN ? AND ? AND expires_at > CURRENT_TIMESTAMP
                """, String.class, userId, stationCode, MatchingDirectionPolicy.HIGH_CONFIDENCE, window[0], window[1]);
        for (String peerId : peers) {
            insertMatch(userId, peerId, null, null, stationCode, occurredAt);
        }
    }

    private void createLineMatches(String userId, String cityCode, String lineId, String stationCode,
                                   Instant occurredAt, double directionConfidence) {
        try {
            matchingLinePresenceCache.register(cityCode, lineId, stationCode, userId, occurredAt);
        } catch (RuntimeException exception) {
            log.warn("matching_line_presence_register_failed userId={}, message={}", shortId(userId), exception.getMessage());
        }

        java.util.LinkedHashSet<String> matched = new java.util.LinkedHashSet<>();

        if (MatchingLinePresenceCache.isRealStation(stationCode)) {
            List<String> stationPeers = new ArrayList<>(matchingLinePresenceCache.stationPeers(
                    cityCode, lineId, stationCode, userId, occurredAt));
            if (stationPeers.isEmpty()) {
                stationPeers = sqlStationPeers(userId, cityCode, lineId, stationCode, occurredAt);
                log.info("matching_station_peers_sql_fallback userId={}, count={}", shortId(userId), stationPeers.size());
            } else {
                log.info("matching_station_peers_redis userId={}, count={}", shortId(userId), stationPeers.size());
            }
            for (String peerId : stationPeers) {
                double peerConfidence = latestDirectionConfidence(peerId);
                if (MatchingDirectionPolicy.blockSameStationDueToOpposite(directionConfidence, peerConfidence)) {
                    log.info("matching_same_station_blocked_opposite userId={}, peerId={}", shortId(userId), shortId(peerId));
                    continue;
                }
                insertMatch(userId, peerId, cityCode, lineId, stationCode, occurredAt);
                matched.add(peerId);
            }
        }

        List<String> linePeers = new ArrayList<>(matchingLinePresenceCache.peers(cityCode, lineId, userId, occurredAt));
        if (linePeers.isEmpty()) {
            linePeers = sqlLinePeers(userId, cityCode, lineId, occurredAt);
            log.info("matching_line_peers_sql_fallback userId={}, count={}", shortId(userId), linePeers.size());
        } else {
            log.info("matching_line_peers_redis userId={}, count={}", shortId(userId), linePeers.size());
        }
        String lineStationCode = "LINE:" + lineId;
        for (String peerId : linePeers) {
            if (matched.contains(peerId)) {
                continue;
            }
            // 同线回退：事件站点用线路占位，便于候选推导 SAME_LINE。
            insertMatch(userId, peerId, cityCode, lineId, lineStationCode, occurredAt);
        }
    }

    private List<String> sqlStationPeers(String userId, String cityCode, String lineId, String stationCode, Instant occurredAt) {
        Timestamp[] window = matchWindow(occurredAt);
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT user_id FROM t_commute_station_summaries
                WHERE user_id <> ? AND city_code = ? AND line_id = ? AND station_code = ?
                  AND occurred_at BETWEEN ? AND ? AND expires_at > CURRENT_TIMESTAMP
                """, String.class, userId, cityCode, lineId, stationCode, window[0], window[1]);
    }

    private List<String> sqlLinePeers(String userId, String cityCode, String lineId, Instant occurredAt) {
        Timestamp[] window = matchWindow(occurredAt);
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT user_id FROM t_commute_station_summaries
                WHERE user_id <> ? AND city_code = ? AND line_id = ?
                  AND occurred_at BETWEEN ? AND ? AND expires_at > CURRENT_TIMESTAMP
                """, String.class, userId, cityCode, lineId, window[0], window[1]);
    }

    private double latestDirectionConfidence(String userId) {
        Double value = jdbcTemplate.query("""
                SELECT direction_confidence FROM t_commute_station_summaries
                WHERE user_id = ? AND expires_at > CURRENT_TIMESTAMP
                ORDER BY occurred_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getDouble(1) : null, userId);
        return value == null ? 0 : value;
    }

    /** 统一用 UTC Timestamp 绑定，与 hibernate.jdbc.time_zone=UTC 对齐。 */
    private static Timestamp[] matchWindow(Instant occurredAt) {
        return new Timestamp[] {
                Timestamp.from(occurredAt.minusSeconds(180)),
                Timestamp.from(occurredAt.plusSeconds(180))
        };
    }

    private void insertMatch(String userId, String peerId, String cityCode, String lineId, String stationCode,
                             Instant occurredAt) {
        String first = userId.compareTo(peerId) < 0 ? userId : peerId;
        String second = userId.compareTo(peerId) < 0 ? peerId : userId;
        String pairKey = first + ":" + second;
        Integer existing;
        Timestamp[] window = matchWindow(occurredAt);
        if (lineId != null) {
            existing = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM t_match_events WHERE pair_key = ? AND line_id = ?
                    AND occurred_at BETWEEN ? AND ? AND expires_at > CURRENT_TIMESTAMP
                    """, Integer.class, pairKey, lineId, window[0], window[1]);
        } else {
            existing = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM t_match_events WHERE pair_key = ? AND station_code = ? AND line_id IS NULL
                    AND occurred_at BETWEEN ? AND ? AND expires_at > CURRENT_TIMESTAMP
                    """, Integer.class, pairKey, stationCode, window[0], window[1]);
        }
        if (existing != null && existing > 0) return;
        String matchId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO t_match_events (id, first_user_id, second_user_id, city_code, line_id, station_code, occurred_at, expires_at, pair_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, matchId, first, second, cityCode, lineId, stationCode,
                Timestamp.from(occurredAt), Timestamp.from(occurredAt.plus(Duration.ofHours(24))), pairKey);
        jdbcTemplate.update("INSERT INTO t_match_unlock_states (id, match_event_id) VALUES (?, ?)", UUID.randomUUID().toString(), matchId);
        log.info("matching_event_created matchId={}, firstUserId={}, secondUserId={}, cityCode={}, lineId={}, stationCode={}, matchReason={}",
                shortId(matchId), shortId(first), shortId(second), cityCode, lineId, stationCode,
                deriveMatchReason(stationCode, lineId, false));
    }

    @Transactional
    public List<Candidate> virtualPassengers(String userId) {
        List<Candidate> real = candidates(userId).stream().filter(candidate -> !candidate.isVirtual()).toList();
        if (!real.isEmpty()) return List.of();

        Candidate existing = findActiveVirtualCandidate(userId);
        if (existing != null) {
            matchingCandidateTtlPolicy.cacheCandidate(existing.matchId(), existing.profileId(), Duration.ofHours(24));
            return List.of(existing);
        }

        Candidate created = createVirtualPassengerSession(userId);
        matchingCandidateTtlPolicy.cacheCandidate(created.matchId(), created.profileId(), Duration.ofHours(24));
        log.info("virtual_passenger_created userId={}, matchId={}, automatedReplyCapability={}",
                shortId(userId), shortId(created.matchId()), created.automatedReplyCapability());
        return List.of(created);
    }

    @Transactional
    public Candidate action(String userId, String matchId, String action, int requestedLevel,
                            boolean consent, boolean realInfoExchangeConfirmed) {
        MatchState state = matchState(matchId, userId);
        boolean first = state.firstUserId().equals(userId);
        int encounters = encounterCount(matchId);
        switch (action) {
            case "LIKE", "COMMENT", "MARK_INTERESTED" -> jdbcTemplate.update(
                    "UPDATE t_match_unlock_states SET " + (first ? "first_user_interested" : "second_user_interested") + " = TRUE WHERE match_event_id = ?", matchId);
            case "REQUEST_UNLOCK" -> {
                if (!consent || requestedLevel < 3) throw new IllegalArgumentException("解锁需要明确同意");
                jdbcTemplate.update("UPDATE t_match_unlock_states SET " + (first ? "first_user_unlock_consent" : "second_user_unlock_consent") + " = TRUE WHERE match_event_id = ?", matchId);
            }
            case "CONFIRM_REAL_INFO_EXCHANGE" -> {
                if (VIRTUAL_USER_ID.equals(state.firstUserId()) || VIRTUAL_USER_ID.equals(state.secondUserId())
                        || encounters < 3) {
                    throw new IllegalArgumentException("共同擦肩次数尚未达到真实信息交换条件");
                }
                jdbcTemplate.update(
                        "UPDATE t_match_unlock_states SET " + (first ? "first_user_real_info_confirmed" : "second_user_real_info_confirmed") + " = TRUE WHERE match_event_id = ?", matchId);
            }
            default -> throw new IllegalArgumentException("不支持的匹配动作");
        }
        jdbcTemplate.update("""
                UPDATE t_match_unlock_states
                SET unlock_level = CASE
                    WHEN first_user_unlock_consent AND second_user_unlock_consent THEN 3
                    WHEN first_user_interested AND second_user_interested THEN 2
                    ELSE unlock_level END
                WHERE match_event_id = ?
                """, matchId);
        Integer level = jdbcTemplate.queryForObject("SELECT unlock_level FROM t_match_unlock_states WHERE match_event_id = ?",
                Integer.class, matchId);
        if (level != null && level >= 3) ensureChatSession(matchId);
        if (encounters >= 3) {
            jdbcTemplate.update("""
                    UPDATE t_match_unlock_states SET unlock_level = 4, real_info_exchange_confirmed = TRUE
                    WHERE match_event_id = ? AND first_user_real_info_confirmed AND second_user_real_info_confirmed
                    """, matchId);
        }
        Candidate candidate = candidateFor(matchId, userId);
        log.info("matching_action_completed userId={}, matchId={}, action={}, unlockLevel={}", shortId(userId), shortId(matchId), action, candidate.unlockLevel());
        return candidate;
    }

    private Candidate candidateFor(String matchId, String userId) {
        return jdbcTemplate.queryForObject("""
                SELECT CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END,
                       COALESCE(profile.avatar_key, 'aurora'), COALESCE(profile.interest_tags, JSON_ARRAY()),
                       state.unlock_level, state.first_user_interested,
                       state.second_user_interested, state.first_user_unlock_consent, state.second_user_unlock_consent,
                       state.real_info_exchange_confirmed, (SELECT id FROM t_chat_sessions WHERE match_event_id = event.id LIMIT 1),
                       event.first_user_id, event.second_user_id, event.station_code, event.line_id
                FROM t_match_events event JOIN t_match_unlock_states state ON state.match_event_id = event.id
                LEFT JOIN t_profiles profile ON profile.user_id = CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END
                WHERE event.id = ? AND (event.first_user_id = ? OR event.second_user_id = ?)
                """, (row, ignored) -> mapCandidate(matchId, row.getString(1), row.getString(2), row.getString(3),
                row.getInt(4), encounterCount(matchId), row.getBoolean(5), row.getBoolean(6), row.getBoolean(7),
                row.getBoolean(8), row.getBoolean(9), row.getString(10), row.getString(11), row.getString(12),
                row.getString(13), row.getString(14), userId),
                userId, userId, matchId, userId, userId);
    }

    private Candidate findActiveVirtualCandidate(String userId) {
        List<Candidate> found = jdbcTemplate.query("""
                SELECT event.id, CASE WHEN event.first_user_id = ? THEN event.second_user_id ELSE event.first_user_id END,
                       COALESCE(profile.avatar_key, 'virtual-sunrise'), COALESCE(profile.interest_tags, JSON_ARRAY('coffee','books')),
                       state.unlock_level, state.first_user_interested, state.second_user_interested,
                       state.first_user_unlock_consent, state.second_user_unlock_consent, state.real_info_exchange_confirmed,
                       (SELECT id FROM t_chat_sessions WHERE match_event_id = event.id LIMIT 1),
                       event.first_user_id, event.second_user_id, event.station_code, event.line_id
                FROM t_match_events event JOIN t_match_unlock_states state ON state.match_event_id = event.id
                LEFT JOIN t_profiles profile ON profile.user_id = ?
                WHERE event.expires_at > CURRENT_TIMESTAMP
                  AND ((event.first_user_id = ? AND event.second_user_id = ?) OR (event.first_user_id = ? AND event.second_user_id = ?))
                ORDER BY event.occurred_at DESC LIMIT 1
                """, (row, ignored) -> mapCandidate(
                row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                row.getInt(5), encounterCount(row.getString(1)), row.getBoolean(6), row.getBoolean(7),
                row.getBoolean(8), row.getBoolean(9), row.getBoolean(10), row.getString(11),
                row.getString(12), row.getString(13), row.getString(14), row.getString(15), userId),
                userId, VIRTUAL_USER_ID, userId, VIRTUAL_USER_ID, VIRTUAL_USER_ID, userId);
        if (found.isEmpty()) return null;
        return found.get(0);
    }

    private Candidate createVirtualPassengerSession(String userId) {
        Instant occurredAt = Instant.now();
        String first = userId.compareTo(VIRTUAL_USER_ID) < 0 ? userId : VIRTUAL_USER_ID;
        String second = userId.compareTo(VIRTUAL_USER_ID) < 0 ? VIRTUAL_USER_ID : userId;
        String matchId = UUID.randomUUID().toString();
        String pairKey = first + ":" + second;
        jdbcTemplate.update("""
                INSERT INTO t_match_events (id, first_user_id, second_user_id, city_code, line_id, station_code, occurred_at, expires_at, pair_key)
                VALUES (?, ?, ?, NULL, NULL, 'VIRTUAL', ?, ?, ?)
                """, matchId, first, second, Timestamp.from(occurredAt),
                Timestamp.from(occurredAt.plus(Duration.ofHours(24))), pairKey);
        jdbcTemplate.update("""
                INSERT INTO t_match_unlock_states (
                    id, match_event_id, unlock_level,
                    first_user_interested, second_user_interested,
                    first_user_unlock_consent, second_user_unlock_consent
                ) VALUES (?, ?, 3, TRUE, TRUE, TRUE, TRUE)
                """, UUID.randomUUID().toString(), matchId);
        // 自动回复服务尚未接入，因此只创建明确标识的体验档案，不创建无人响应的假聊天。
        return candidateFor(matchId, userId);
    }

    private Candidate mapCandidate(String matchId, String profileId, String avatarKey, String interestTagsJson,
                                   int unlockLevel, int encounterCount, boolean firstInterested, boolean secondInterested,
                                   boolean firstConsent, boolean secondConsent, boolean realInfoConfirmed,
                                   String chatSessionId, String firstUserId, String secondUserId,
                                   String stationCode, String lineId, String currentUserId) {
        boolean virtual = VIRTUAL_USER_ID.equals(profileId)
                || VIRTUAL_USER_ID.equals(firstUserId)
                || VIRTUAL_USER_ID.equals(secondUserId);
        String avatar = virtual && (avatarKey == null || avatarKey.isBlank() || "aurora".equals(avatarKey))
                ? "virtual-sunrise" : avatarKey;
        List<String> tags = parseInterestTags(interestTagsJson);
        if (virtual && tags.isEmpty()) {
            tags = List.of("音乐", "阅读");
        }
        String matchReason = deriveMatchReason(stationCode, lineId, virtual);
        Journey journey = journey(lineId, stationCode);
        int sharedInterestCount = virtual ? tags.size() : sharedInterestCount(currentUserId, profileId);
        int encounterDays = encounterDays(matchId);
        return new Candidate(matchId, profileId, avatar, tags, unlockLevel, encounterCount,
                firstInterested, secondInterested, firstConsent, secondConsent, realInfoConfirmed, virtual,
                virtual ? null : chatSessionId, matchReason, journey,
                new UnlockProgress(Math.max(1, unlockLevel), 4, nextUnlockText(unlockLevel)),
                encounterCount, sharedInterestCount, encounterDays,
                virtual ? "NOT_CONFIGURED" : "NOT_APPLICABLE", 3,
                !virtual && encounterCount >= 3);
    }

    private Journey journey(String lineId, String stationCode) {
        if (lineId == null || lineId.isBlank()) return null;
        List<LineInfo> lines = jdbcTemplate.query(
                "SELECT line_name FROM t_metro_lines WHERE line_id = ? LIMIT 1",
                (row, ignored) -> new LineInfo(row.getString(1)), lineId);
        if (lines.isEmpty()) return null;
        String lineName = lines.get(0).name();
        List<StationInfo> stations = jdbcTemplate.query("""
                SELECT station_name, station_code, sequence_no FROM t_metro_stations
                WHERE line_id = ? ORDER BY sequence_no
                """, (row, ignored) -> new StationInfo(row.getString(1), row.getString(2), row.getInt(3)), lineId);
        // 当前契约尚无上/下行方向，不能用线路顺序默认推断目的地、下一站和剩余时间。
        return new Journey(lineId, lineName, MetroContracts.lineColor(lineName), null, null, null, 0, 0);
    }

    private int sharedInterestCount(String currentUserId, String profileId) {
        if (currentUserId == null || profileId == null || currentUserId.equals(profileId)) return 0;
        List<InterestInfo> rows = jdbcTemplate.query("""
                SELECT user_id, COALESCE(interest_tags, JSON_ARRAY()) FROM t_profiles
                WHERE user_id IN (?, ?)
                """, (row, ignored) -> new InterestInfo(row.getString(1), row.getString(2)), currentUserId, profileId);
        Set<String> mine = new HashSet<>();
        Set<String> theirs = new HashSet<>();
        for (InterestInfo row : rows) {
            if (currentUserId.equals(row.userId())) mine.addAll(parseInterestTags(row.tagsJson()));
            if (profileId.equals(row.userId())) theirs.addAll(parseInterestTags(row.tagsJson()));
        }
        mine.retainAll(theirs);
        return mine.size();
    }

    private int encounterDays(String matchId) {
        Integer days = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT DATE(event.occurred_at))
                FROM t_match_events current_event JOIN t_match_events event ON event.pair_key = current_event.pair_key
                WHERE current_event.id = ?
                """, Integer.class, matchId);
        return days == null ? 0 : days;
    }

    private static String nextUnlockText(int unlockLevel) {
        return switch (unlockLevel) {
            case 1 -> "双方感兴趣后解锁更多共同点";
            case 2 -> "双方同意后开启匿名聊天";
            case 3 -> "再次相遇并共同确认后交换真实信息";
            default -> "已完成全部解锁";
        };
    }

    public static String deriveMatchReason(String stationCode, String lineId, boolean virtual) {
        if (virtual) {
            return null;
        }
        if (MatchingLinePresenceCache.isRealStation(stationCode)) {
            return "SAME_STATION";
        }
        if (lineId != null && !lineId.isBlank()) {
            return "SAME_LINE";
        }
        return "SAME_LINE";
    }

    private List<String> parseInterestTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            log.warn("interest_tags_parse_failed message={}", exception.getMessage());
            return List.of();
        }
    }

    private int encounterCount(String matchId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_match_events current_event JOIN t_match_events event ON event.pair_key = current_event.pair_key
                WHERE current_event.id = ? AND event.expires_at > CURRENT_TIMESTAMP
                """, Integer.class, matchId);
        return count == null ? 0 : count;
    }

    private MatchState matchState(String matchId, String userId) {
        List<MatchState> states = jdbcTemplate.query("""
                SELECT first_user_id, second_user_id, pair_key FROM t_match_events
                WHERE id = ? AND (first_user_id = ? OR second_user_id = ?) AND expires_at > CURRENT_TIMESTAMP
                """, (row, ignored) -> new MatchState(row.getString(1), row.getString(2), row.getString(3)), matchId, userId, userId);
        if (states.isEmpty()) throw new ResourceAccessDeniedException("匹配不存在或无权操作");
        return states.get(0);
    }

    private void ensureChatSession(String matchId) {
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_chat_sessions WHERE match_event_id = ?", Integer.class, matchId);
        if (existing != null && existing > 0) return;
        Instant rideEndsAt = Instant.now().plus(Duration.ofMinutes(30));
        jdbcTemplate.update("""
                INSERT INTO t_chat_sessions (id, match_event_id, ride_ends_at, reply_window_ends_at, status)
                VALUES (?, ?, ?, ?, 'RIDING')
                """, UUID.randomUUID().toString(), matchId, rideEndsAt, rideEndsAt.plus(Duration.ofHours(24)));
    }

    private record MatchState(String firstUserId, String secondUserId, String pairKey) { }
    private record LineInfo(String name) { }
    private record StationInfo(String name, String code, int sequence) { }
    private record InterestInfo(String userId, String tagsJson) { }

    public Topic rescueTopic(String stationName, String trafficSummary) {
        return rescueTopic("", stationName, trafficSummary);
    }

    public Topic rescueTopic(String userId, String stationName, String trafficSummary) {
        String key = userId == null ? "" : userId;
        String previous = lastTopicByUser.get(key);
        String next = TopicRescuePrompts.pick(stationName, trafficSummary, previous);
        if (!key.isBlank()) {
            lastTopicByUser.put(key, next);
        }
        return new Topic(next);
    }

    private String shortId(String value) { return value == null ? "none" : value.length() <= 8 ? value : value.substring(0, 8); }
}
