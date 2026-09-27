package com.glint.server.common.presentation;

import java.time.Instant;
import java.util.List;

public final class PresentationContracts {

    private PresentationContracts() {
    }

    public record HomeSummary(int encounterCount, int interestedCount, int chatCount,
                              int mutualCount, int unreadNotificationCount) {
    }

    public record ProfileDashboard(String displayName, String avatarKey, String title,
                                   int encounterCount, int unlockedCount, int boardPostCount,
                                   int unlockedAchievementCount, int totalAchievementCount) {
    }

    public record Achievement(String code, String name, String iconKey, String conditionText,
                              int currentValue, int targetValue, boolean unlocked, Instant earnedAt) {
    }

    public record CommutePreferences(String startTime, String endTime, List<String> preferredLineIds,
                                     boolean autoMatch, boolean notificationsEnabled, boolean stealth,
                                     String favoriteStationCode, String favoriteStationName) {
    }
}
