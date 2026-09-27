package com.glint.server.web.report;

import com.glint.server.biz.report.CommuteReportService;
import com.glint.server.common.dto.ApiResponse;
import com.glint.server.common.report.ReportContracts.CommuteReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 每日通勤报告接口。 */
@RestController
@RequestMapping("/api/v1/commute-reports")
public class CommuteReportController {

    private static final Logger log = LoggerFactory.getLogger(CommuteReportController.class);
    private final CommuteReportService commuteReportService;

    public CommuteReportController(CommuteReportService commuteReportService) {
        this.commuteReportService = commuteReportService;
    }

    @GetMapping("/today")
    public ApiResponse<CommuteReport> today(@RequestAttribute("authenticatedUserId") String userId) {
        CommuteReport report = commuteReportService.today(userId);
        log.info("commute_report_returned userId={}, reportId={}, encounters={}", shortId(userId), shortId(report.reportId()), report.encounterCount());
        return ApiResponse.ok(report);
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
