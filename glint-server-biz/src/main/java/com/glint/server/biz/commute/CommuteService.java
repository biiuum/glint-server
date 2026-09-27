package com.glint.server.biz.commute;

import com.glint.server.biz.metro.MetroNetworkService;
import com.glint.server.common.commute.CommuteContracts.StationSummary;
import com.glint.server.dal.commute.CommuteStationSummaryEntity;
import com.glint.server.dal.commute.CommuteStationSummaryRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
/** 保存站点级通勤摘要并触发派生匹配的服务。 */
public class CommuteService {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(CommuteService.class);

    private final CommuteStationSummaryRepository commuteStationSummaryRepository;
    private final com.glint.server.biz.matching.MatchingService matchingService;
    private final MetroNetworkService metroNetworkService;

    public CommuteService(CommuteStationSummaryRepository commuteStationSummaryRepository,
                          com.glint.server.biz.matching.MatchingService matchingService,
                          MetroNetworkService metroNetworkService) {
        this.commuteStationSummaryRepository = commuteStationSummaryRepository;
        this.matchingService = matchingService;
        this.metroNetworkService = metroNetworkService;
    }

    public StationSummary record(String userId, String cityCode, String lineId, String stationCode, String stationName,
                                 Instant occurredAt, String statusSummary, double directionConfidence) {
        String resolvedCity = blankToNull(cityCode);
        String resolvedLine = blankToNull(lineId);
        validateMetroContext(resolvedCity, resolvedLine);

        String id = UUID.randomUUID().toString();
        Instant expiresAt = occurredAt.plus(RETENTION);
        String resolvedStationCode = blankTo(stationCode, resolvedLine != null ? "LINE:" + resolvedLine : "UNKNOWN");
        String resolvedStationName = blankTo(stationName, "同线路");
        commuteStationSummaryRepository.save(CommuteStationSummaryEntity.create(
                id, userId, resolvedCity, resolvedLine, resolvedStationCode, resolvedStationName,
                occurredAt, expiresAt, statusSummary, directionConfidence
        ));
        if (!isStealth(statusSummary)) {
            matchingService.createMatchesForSummary(userId, resolvedCity, resolvedLine,
                    resolvedStationCode, occurredAt, directionConfidence);
        } else {
            log.info("matching_skipped_stealth userId={}, summaryId={}", shortId(userId), shortId(id));
        }
        log.info("commute_summary_persisted userId={}, summaryId={}, cityCode={}, lineId={}, stationCode={}",
                shortId(userId), shortId(id), resolvedCity, resolvedLine, resolvedStationCode);
        return new StationSummary(id, resolvedCity, resolvedLine, resolvedStationCode,
                resolvedStationName, occurredAt, statusSummary, directionConfidence, expiresAt);
    }

    private void validateMetroContext(String cityCode, String lineId) {
        if (cityCode == null && lineId == null) {
            return;
        }
        if (cityCode == null || lineId == null) {
            throw new IllegalArgumentException("城市与线路须同时提供");
        }
        if (!metroNetworkService.lineExistsInCity(cityCode, lineId)) {
            throw new IllegalArgumentException("城市或线路不存在");
        }
    }

    private static boolean isStealth(String statusSummary) {
        return statusSummary != null && statusSummary.trim().equalsIgnoreCase("stealth");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
