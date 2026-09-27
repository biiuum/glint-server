package com.glint.server.dal.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 同线 / 同站实时在场索引：按城市+线路(+站点)+时间桶把用户写入 Redis SET。
 * <ul>
 *   <li>线桶 Key: {@code matching:line:{cityCode}:{lineId}:{bucket}}</li>
 *   <li>站桶 Key: {@code matching:station:{cityCode}:{lineId}:{stationCode}:{bucket}}</li>
 * </ul>
 * bucket = epochSecond / 180。
 */
@Repository
public class MatchingLinePresenceCache {

    private static final String LINE_KEY_PREFIX = "matching:line:";
    private static final String STATION_KEY_PREFIX = "matching:station:";
    private static final long BUCKET_SECONDS = 180L;
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(30);
    private static final Logger log = LoggerFactory.getLogger(MatchingLinePresenceCache.class);

    private final StringRedisTemplate redisTemplate;

    public MatchingLinePresenceCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 写入线桶；若站点为真实站点则同时写入站桶。 */
    public void register(String cityCode, String lineId, String stationCode, String userId, Instant occurredAt) {
        long bucket = bucket(occurredAt);
        String lineKey = lineKey(cityCode, lineId, bucket);
        redisTemplate.opsForSet().add(lineKey, userId);
        redisTemplate.expire(lineKey, PRESENCE_TTL);
        if (isRealStation(stationCode)) {
            String stationKey = stationKey(cityCode, lineId, stationCode, bucket);
            redisTemplate.opsForSet().add(stationKey, userId);
            redisTemplate.expire(stationKey, PRESENCE_TTL);
        }
    }

    /** 兼容旧调用：仅写线桶。 */
    public void register(String cityCode, String lineId, String userId, Instant occurredAt) {
        register(cityCode, lineId, null, userId, occurredAt);
    }

    /** 查询相邻三个时间桶内的同站同伴（不含自己）。 */
    public Set<String> stationPeers(String cityCode, String lineId, String stationCode, String userId, Instant occurredAt) {
        if (!isRealStation(stationCode)) {
            return Set.of();
        }
        return peersAcrossBuckets(userId, occurredAt,
                bucket -> stationKey(cityCode, lineId, stationCode, bucket),
                "matching_station_presence_read_failed");
    }

    /** 查询相邻三个时间桶内的同线同伴（不含自己）。Redis 异常时返回空集，由调用方回退 SQL。 */
    public Set<String> peers(String cityCode, String lineId, String userId, Instant occurredAt) {
        return peersAcrossBuckets(userId, occurredAt,
                bucket -> lineKey(cityCode, lineId, bucket),
                "matching_line_presence_read_failed");
    }

    private Set<String> peersAcrossBuckets(String userId, Instant occurredAt,
                                           java.util.function.LongFunction<String> keyFn,
                                           String warnEvent) {
        try {
            long bucket = bucket(occurredAt);
            Set<String> peers = new HashSet<>();
            for (long offset = -1; offset <= 1; offset++) {
                Set<String> members = redisTemplate.opsForSet().members(keyFn.apply(bucket + offset));
                if (members != null) {
                    peers.addAll(members);
                }
            }
            peers.remove(userId);
            return peers;
        } catch (RuntimeException exception) {
            log.warn("{} message={}", warnEvent, exception.getMessage());
            return Set.of();
        }
    }

    /** 真实站点：非空且不是线路占位 / UNKNOWN / VIRTUAL。 */
    public static boolean isRealStation(String stationCode) {
        if (stationCode == null || stationCode.isBlank()) {
            return false;
        }
        String trimmed = stationCode.trim();
        return !trimmed.equalsIgnoreCase("UNKNOWN")
                && !trimmed.equalsIgnoreCase("VIRTUAL")
                && !trimmed.regionMatches(true, 0, "LINE:", 0, 5);
    }

    static long bucket(Instant occurredAt) {
        return occurredAt.getEpochSecond() / BUCKET_SECONDS;
    }

    private static String lineKey(String cityCode, String lineId, long bucket) {
        return LINE_KEY_PREFIX + cityCode + ":" + lineId + ":" + bucket;
    }

    private static String stationKey(String cityCode, String lineId, String stationCode, long bucket) {
        return STATION_KEY_PREFIX + cityCode + ":" + lineId + ":" + stationCode + ":" + bucket;
    }
}
