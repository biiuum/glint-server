package com.glint.server.common.report;

import java.time.LocalDate;
import java.util.List;

public final class ReportContracts {

    private ReportContracts() {
    }

    public record CommuteReport(String reportId, LocalDate reportDate, int encounterCount, int sameRouteCount,
                                int sharedInterestCount, String sharedInterestLabel, int earlierMinutes,
                                int averageMinutes, int weekOverWeekPercent, List<DailyTrend> dailyTrend) {
        public CommuteReport(String reportId, LocalDate reportDate, int encounterCount, int sameRouteCount) {
            this(reportId, reportDate, encounterCount, sameRouteCount, 0, "共同兴趣", 0, 0, 0, List.of());
        }
    }

    public record DailyTrend(LocalDate date, int encounterCount) {
    }
}
