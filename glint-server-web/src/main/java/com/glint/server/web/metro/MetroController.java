package com.glint.server.web.metro;

import com.glint.server.biz.metro.MetroNetworkService;
import com.glint.server.common.dto.ApiResponse;
import com.glint.server.common.metro.MetroContracts.MetroCity;
import com.glint.server.common.metro.MetroContracts.MetroLine;
import com.glint.server.common.metro.MetroContracts.MetroStation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metro")
public class MetroController {

    private final MetroNetworkService metroNetworkService;
    private final MetroSyncAdminGuard metroSyncAdminGuard;

    public MetroController(MetroNetworkService metroNetworkService, MetroSyncAdminGuard metroSyncAdminGuard) {
        this.metroNetworkService = metroNetworkService;
        this.metroSyncAdminGuard = metroSyncAdminGuard;
    }

    @GetMapping("/cities")
    public ApiResponse<List<MetroCity>> cities() {
        return ApiResponse.ok(metroNetworkService.listCities());
    }

    @GetMapping("/cities/{cityCode}/lines")
    public ApiResponse<List<MetroLine>> lines(@PathVariable String cityCode) {
        return ApiResponse.ok(metroNetworkService.listLines(cityCode));
    }

    @GetMapping("/lines/{lineId}/stations")
    public ApiResponse<List<MetroStation>> stations(@PathVariable String lineId) {
        return ApiResponse.ok(metroNetworkService.listStations(lineId));
    }

    @GetMapping("/cities/{cityCode}/stations")
    public ApiResponse<List<MetroStation>> cityStations(@PathVariable String cityCode) {
        return ApiResponse.ok(metroNetworkService.listStationsByCity(cityCode));
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Integer>> sync(
            @RequestHeader(value = "X-Glint-Admin-Key", required = false) String adminKey) {
        metroSyncAdminGuard.requireAdmin(adminKey);
        return ApiResponse.ok(Map.of("syncedCities", metroNetworkService.syncAll()));
    }
}
