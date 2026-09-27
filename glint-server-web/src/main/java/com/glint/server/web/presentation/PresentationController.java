package com.glint.server.web.presentation;

import com.glint.server.biz.presentation.AchievementService;
import com.glint.server.biz.presentation.CommutePreferenceService;
import com.glint.server.biz.presentation.PresentationService;
import com.glint.server.common.dto.ApiResponse;
import com.glint.server.common.presentation.PresentationContracts.Achievement;
import com.glint.server.common.presentation.PresentationContracts.CommutePreferences;
import com.glint.server.common.presentation.PresentationContracts.HomeSummary;
import com.glint.server.common.presentation.PresentationContracts.ProfileDashboard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
/** 高保真页面使用的聚合展示资源；身份只从 Bearer 会话派生。 */
public class PresentationController {

    private final PresentationService presentationService;
    private final AchievementService achievementService;
    private final CommutePreferenceService commutePreferenceService;

    public PresentationController(PresentationService presentationService, AchievementService achievementService,
                                  CommutePreferenceService commutePreferenceService) {
        this.presentationService = presentationService;
        this.achievementService = achievementService;
        this.commutePreferenceService = commutePreferenceService;
    }

    @GetMapping("/home/summary")
    public ApiResponse<HomeSummary> home(@RequestAttribute("authenticatedUserId") String userId) {
        return ApiResponse.ok(presentationService.homeSummary(userId));
    }

    @GetMapping("/profiles/me/dashboard")
    public ApiResponse<ProfileDashboard> dashboard(@RequestAttribute("authenticatedUserId") String userId) {
        return ApiResponse.ok(presentationService.profileDashboard(userId));
    }

    @GetMapping("/achievements")
    public ApiResponse<List<Achievement>> achievements(@RequestAttribute("authenticatedUserId") String userId) {
        return ApiResponse.ok(achievementService.achievements(userId));
    }

    @GetMapping("/commute-preferences/me")
    public ApiResponse<CommutePreferences> preferences(@RequestAttribute("authenticatedUserId") String userId) {
        return ApiResponse.ok(commutePreferenceService.current(userId));
    }

    @PutMapping("/commute-preferences/me")
    public ApiResponse<CommutePreferences> savePreferences(@RequestAttribute("authenticatedUserId") String userId,
                                                           @Valid @RequestBody PreferenceRequest request) {
        return ApiResponse.ok(commutePreferenceService.save(userId, request.contract()));
    }

    public record PreferenceRequest(
            @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String startTime,
            @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String endTime,
            @Size(max = 20) List<@NotBlank @Size(max = 64) String> preferredLineIds,
            boolean autoMatch,
            boolean notificationsEnabled,
            boolean stealth,
            @Size(max = 64) String favoriteStationCode,
            @Size(max = 128) String favoriteStationName
    ) {
        CommutePreferences contract() {
            return new CommutePreferences(startTime, endTime,
                    preferredLineIds == null ? List.of() : preferredLineIds,
                    autoMatch, notificationsEnabled, stealth, favoriteStationCode, favoriteStationName);
        }
    }
}
