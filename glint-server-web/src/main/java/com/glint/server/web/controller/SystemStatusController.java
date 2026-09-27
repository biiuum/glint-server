package com.glint.server.web.controller;

import com.glint.server.biz.system.SystemStatus;
import com.glint.server.biz.system.SystemStatusService;
import com.glint.server.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 服务健康状态接口。 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusController.class);
    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public ApiResponse<SystemStatus> status() {
        SystemStatus status = systemStatusService.currentStatus();
        log.info("system_status_returned status={}", status.status());
        return ApiResponse.ok(status);
    }
}
