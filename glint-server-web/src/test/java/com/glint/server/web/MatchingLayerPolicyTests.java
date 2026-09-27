package com.glint.server.web;

import com.glint.server.biz.matching.MatchingDirectionPolicy;
import com.glint.server.biz.matching.MatchingService;
import com.glint.server.biz.matching.TopicRescuePrompts;
import com.glint.server.dal.matching.MatchingLinePresenceCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 同站优先 / 同线回退相关纯逻辑单测（不依赖 Redis/DB）。 */
class MatchingLayerPolicyTests {

    @Test
    void treatsLinePlaceholderAsNonRealStation() {
        assertTrue(MatchingLinePresenceCache.isRealStation("sh-rmgc"));
        assertFalse(MatchingLinePresenceCache.isRealStation(null));
        assertFalse(MatchingLinePresenceCache.isRealStation(""));
        assertFalse(MatchingLinePresenceCache.isRealStation("UNKNOWN"));
        assertFalse(MatchingLinePresenceCache.isRealStation("VIRTUAL"));
        assertFalse(MatchingLinePresenceCache.isRealStation("LINE:L2"));
        assertFalse(MatchingLinePresenceCache.isRealStation("line:l2"));
    }

    @Test
    void derivesMatchReasonFromStationCode() {
        assertEquals("SAME_STATION", MatchingService.deriveMatchReason("sh-rmgc", "L2", false));
        assertEquals("SAME_LINE", MatchingService.deriveMatchReason("LINE:L2", "L2", false));
        assertEquals("SAME_LINE", MatchingService.deriveMatchReason("UNKNOWN", "L2", false));
        assertEquals(null, MatchingService.deriveMatchReason("VIRTUAL", null, true));
    }

    @Test
    void pickAvoidsImmediateRepeatWhenPoolHasAlternatives() {
        String first = TopicRescuePrompts.pick("顾唐路", "正常运行", null);
        assertFalse(first.isBlank());
        String second = TopicRescuePrompts.pick("顾唐路", "正常运行", first);
        assertNotEquals(first, second);
    }

    @Test
    void doesNotBlockSameStationWithoutDirectionAxis() {
        assertTrue(MatchingDirectionPolicy.bothHighConfidence(0.95, 0.92));
        assertFalse(MatchingDirectionPolicy.isOppositeDirection(0.95, 0.92));
        assertFalse(MatchingDirectionPolicy.blockSameStationDueToOpposite(0.95, 0.92));
        assertTrue(MatchingDirectionPolicy.hasUsableConfidence(0.55));
        assertFalse(MatchingDirectionPolicy.hasUsableConfidence(0.2));
    }
}
