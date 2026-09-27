package com.glint.server.web.safety;

import com.glint.server.biz.safety.SafetyService;
import com.glint.server.common.dto.ApiResponse;
import com.glint.server.common.safety.SafetyContracts.MessageReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 消息举报接口；日志不记录举报原因正文。 */
@RestController
@RequestMapping("/api/v1/safety/message-reports")
public class SafetyController {

    private static final Logger log = LoggerFactory.getLogger(SafetyController.class);
    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageReport> report(@RequestAttribute("authenticatedUserId") String userId,
                                              @Valid @RequestBody MessageReportRequest request) {
        log.info("message_report_requested userId={}, messageId={}, reasonLength={}", shortId(userId), shortId(request.messageId()), request.reason().length());
        MessageReport report = safetyService.report(request.messageId(), userId, request.reason());
        log.info("message_report_created userId={}, reportId={}", shortId(userId), shortId(report.reportId()));
        return ApiResponse.ok(report);
    }

    public record MessageReportRequest(
            @NotBlank @Size(max = 64) String messageId,
            @NotBlank @Size(max = 128) String reason
    ) {
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
