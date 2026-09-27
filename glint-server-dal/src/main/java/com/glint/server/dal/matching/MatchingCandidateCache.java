package com.glint.server.dal.matching;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Repository
public class MatchingCandidateCache {

    private static final String KEY_PREFIX = "matching:candidate:";
    private static final Duration MAX_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public MatchingCandidateCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Duration put(String candidateId, String value, Duration requestedTtl) {
        if (requestedTtl == null || requestedTtl.isZero() || requestedTtl.isNegative()) {
            throw new IllegalArgumentException("匹配候选缓存 TTL 必须为正数");
        }
        Duration ttl = requestedTtl.compareTo(MAX_TTL) > 0 ? MAX_TTL : requestedTtl;
        redisTemplate.opsForValue().set(key(candidateId), value, ttl);
        return ttl;
    }

    public Duration remainingTtl(String candidateId) {
        Long milliseconds = redisTemplate.getExpire(key(candidateId), TimeUnit.MILLISECONDS);
        return milliseconds == null || milliseconds < 0 ? Duration.ZERO : Duration.ofMillis(milliseconds);
    }

    private String key(String candidateId) {
        return KEY_PREFIX + candidateId;
    }
}
