package com.glint.server.web.commute;

import com.glint.server.biz.commute.CommuteService;
import com.glint.server.common.commute.CommuteContracts.StationSummary;
import com.glint.server.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/commutes")
/** 仅接收站点级派生摘要的通勤接口。 */
public class CommuteController {

    private static final Logger log = LoggerFactory.getLogger(CommuteController.class);

    private final CommuteService commuteService;

    public CommuteController(CommuteService commuteService) {
        this.commuteService = commuteService;
    }

    @PostMapping({"/start", "/end", "/station-summaries"})
    public ApiResponse<StationSummary> record(
            @RequestAttribute("authenticatedUserId") String userId,
            @Valid @RequestBody StationSummaryRequest request
    ) {
        request.validateContext();
        log.info("commute_summary_requested userId={}, cityCode={}, lineId={}, stationCode={}, status={}",
                shortId(userId), request.cityCode(), request.lineId(), request.stationCode(), request.statusSummary());
        StationSummary summary = commuteService.record(
                userId, request.cityCode(), request.lineId(), request.stationCode(), request.stationName(),
                request.occurredAt(), request.statusSummary(), request.directionConfidence()
        );
        log.info("commute_summary_recorded userId={}, summaryId={}", shortId(userId), shortId(summary.id()));
        return ApiResponse.ok(summary);
    }

    public record StationSummaryRequest(
            @Size(max = 16) String cityCode,
            @Size(max = 64) String lineId,
            @Size(max = 64) String stationCode,
            @Size(max = 128) String stationName,
            @NotNull Instant occurredAt,
            @NotBlank @Size(max = 256) String statusSummary,
            @DecimalMin("0.0") @DecimalMax("1.0") double directionConfidence
    ) {
        void validateContext() {
            boolean hasStation = stationCode != null && !stationCode.isBlank()
                    && stationName != null && !stationName.isBlank();
            boolean hasLine = cityCode != null && !cityCode.isBlank()
                    && lineId != null && !lineId.isBlank();
            if (!hasStation && !hasLine) {
                throw new IllegalArgumentException("通勤摘要须包含站点或城市线路");
            }
        }
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
