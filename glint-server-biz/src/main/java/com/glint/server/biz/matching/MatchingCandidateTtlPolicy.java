package com.glint.server.biz.matching;

import com.glint.server.dal.matching.MatchingCandidateCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Service
/** 将候选缓存生命周期限制在 PRD 规定的 24 小时以内。 */
public class MatchingCandidateTtlPolicy {

    public static final Duration MAX_TTL = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(MatchingCandidateTtlPolicy.class);

    private final MatchingCandidateCache matchingCandidateCache;

    public MatchingCandidateTtlPolicy() {
        this.matchingCandidateCache = null;
    }

    @Autowired
    public MatchingCandidateTtlPolicy(MatchingCandidateCache matchingCandidateCache) {
        this.matchingCandidateCache = matchingCandidateCache;
    }

    public Duration cacheTtl(Duration requestedTtl) {
        if (requestedTtl == null || requestedTtl.isZero() || requestedTtl.isNegative()) {
            throw new IllegalArgumentException("匹配候选缓存 TTL 必须为正数");
        }
        return requestedTtl.compareTo(MAX_TTL) > 0 ? MAX_TTL : requestedTtl;
    }

    public Duration cacheCandidate(String candidateId, String value, Duration requestedTtl) {
        if (matchingCandidateCache == null) {
            throw new IllegalStateException("匹配候选 Redis 缓存不可用");
        }
        Duration ttl = matchingCandidateCache.put(candidateId, value, cacheTtl(requestedTtl));
        log.info("matching_candidate_cached candidateId={}, ttlSeconds={}", shortId(candidateId), ttl.toSeconds());
        return ttl;
    }

    public Duration remainingTtl(String candidateId) {
        if (matchingCandidateCache == null) {
            throw new IllegalStateException("匹配候选 Redis 缓存不可用");
        }
        return matchingCandidateCache.remainingTtl(candidateId);
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
