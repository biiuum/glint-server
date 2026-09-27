package com.glint.server.dal.commute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "t_commute_station_summaries")
public class CommuteStationSummaryEntity {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "city_code")
    private String cityCode;

    @Column(name = "line_id")
    private String lineId;

    @Column(name = "station_code", nullable = false)
    private String stationCode;

    @Column(name = "station_name", nullable = false)
    private String stationName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status_summary", nullable = false)
    private String statusSummary;

    @Column(name = "direction_confidence", nullable = false)
    private double directionConfidence;

    protected CommuteStationSummaryEntity() {
    }

    private CommuteStationSummaryEntity(String id, String userId, String cityCode, String lineId, String stationCode,
                                        String stationName, Instant occurredAt, Instant expiresAt, String statusSummary,
                                        double directionConfidence) {
        this.id = id;
        this.userId = userId;
        this.cityCode = cityCode;
        this.lineId = lineId;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.occurredAt = occurredAt;
        this.expiresAt = expiresAt;
        this.statusSummary = statusSummary;
        this.directionConfidence = directionConfidence;
    }

    public static CommuteStationSummaryEntity create(String id, String userId, String cityCode, String lineId,
                                                     String stationCode, String stationName, Instant occurredAt,
                                                     Instant expiresAt, String statusSummary, double directionConfidence) {
        return new CommuteStationSummaryEntity(id, userId, cityCode, lineId, stationCode, stationName, occurredAt,
                expiresAt, statusSummary, directionConfidence);
    }
}
