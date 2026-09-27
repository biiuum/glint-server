package com.glint.server.common.matching;

import java.util.List;

public final class MatchingContracts {

    private MatchingContracts() {
    }

    public record Candidate(String matchId, String profileId, String avatarKey, List<String> interestTags,
                            int unlockLevel, int encounterCount, boolean firstUserInterested,
                            boolean secondUserInterested, boolean firstUserUnlockConsent,
                            boolean secondUserUnlockConsent, boolean realInfoExchangeConfirmed,
                            boolean isVirtual, String chatSessionId, String matchReason,
                            Journey journey, UnlockProgress unlockProgress,
                            int sameRouteCount, int sharedInterestCount, int encounterDays,
                            String automatedReplyCapability, int realInfoRequiredEncounters,
                            boolean realInfoExchangeEligible) {
        public Candidate(String matchId, String profileId, String avatarKey, List<String> interestTags,
                         int unlockLevel, int encounterCount, boolean firstUserInterested,
                         boolean secondUserInterested, boolean firstUserUnlockConsent,
                         boolean secondUserUnlockConsent, boolean realInfoExchangeConfirmed,
                         boolean isVirtual, String chatSessionId, String matchReason) {
            this(matchId, profileId, avatarKey, interestTags, unlockLevel, encounterCount,
                    firstUserInterested, secondUserInterested, firstUserUnlockConsent,
                    secondUserUnlockConsent, realInfoExchangeConfirmed, isVirtual, chatSessionId, matchReason,
                    null, new UnlockProgress(Math.max(1, unlockLevel), 4, nextUnlockText(unlockLevel)),
                    encounterCount, 0, encounterCount > 0 ? 1 : 0,
                    isVirtual ? "NOT_CONFIGURED" : "NOT_APPLICABLE", 3,
                    !isVirtual && encounterCount >= 3);
        }
    }

    public record Journey(String lineId, String lineName, String lineColorHex, String directionName,
                          String currentStationName, String nextStationName,
                          Integer remainingStations, Integer estimatedMinutes) {
    }

    public record UnlockProgress(int current, int target, String nextUnlockText) {
    }

    public record Topic(String topic) {
    }

    private static String nextUnlockText(int level) {
        return switch (level) {
            case 1 -> "双方感兴趣后解锁互动信息";
            case 2 -> "双方同意后解锁限时聊天";
            case 3 -> "再次同路并确认后可交换更多信息";
            default -> "已解锁当前全部匿名资料";
        };
    }
}
