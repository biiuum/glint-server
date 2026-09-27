package com.glint.server.biz.safety;

import java.time.Duration;
import java.time.Instant;

public class RetentionPolicy {

    public static final Duration STATION_AND_MATCH_RETENTION = Duration.ofHours(24);
    public static final Duration MESSAGE_AND_REPORT_RETENTION = Duration.ofDays(7);

    public Instant stationSummaryExpiresAt(Instant createdAt) {
        return createdAt.plus(STATION_AND_MATCH_RETENTION);
    }

    public Instant messageExpiresAt(Instant createdAt) {
        return createdAt.plus(MESSAGE_AND_REPORT_RETENTION);
    }

    public boolean isExpired(Instant createdAt, Duration retention, Instant now) {
        return !createdAt.plus(retention).isAfter(now);
    }
}
