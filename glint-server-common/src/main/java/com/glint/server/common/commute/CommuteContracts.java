package com.glint.server.common.commute;

import java.time.Instant;

public final class CommuteContracts {

    private CommuteContracts() {
    }

    public record StationSummary(String id, String cityCode, String lineId, String stationCode, String stationName,
                                 Instant occurredAt, String statusSummary, double directionConfidence,
                                 Instant expiresAt) {
    }
}
