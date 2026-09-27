package com.glint.server.web.profile;

import com.glint.server.biz.profile.ProfileService;
import com.glint.server.common.dto.ApiResponse;
import com.glint.server.common.profile.ProfileContracts.AnonymousProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ApiResponse<AnonymousProfile> current(@RequestAttribute("authenticatedUserId") String userId) {
        log.info("profile_current_requested userId={}", shortId(userId));
        return ApiResponse.ok(profileService.current(userId));
    }

    @PutMapping("/me")
    public ApiResponse<AnonymousProfile> save(@RequestAttribute("authenticatedUserId") String userId,
                                              @Valid @RequestBody ProfileUpdateRequest request) {
        log.info("profile_save_requested userId={}, tagCount={}, hasNickname={}, hasRegion={}", shortId(userId),
                request.interestTags().size(), request.nickname() != null && !request.nickname().isBlank(),
                request.regionName() != null && !request.regionName().isBlank());
        return ApiResponse.ok(profileService.save(userId, request.avatarKey(), request.interestTags(),
                request.nickname(), request.regionCode(), request.regionName()));
    }

    /** 只允许通勤工作流切换状态，不暴露到资料编辑表单。 */
    @PutMapping("/me/commute-status")
    public ApiResponse<AnonymousProfile> updateCommuteStatus(@RequestAttribute("authenticatedUserId") String userId,
                                                              @Valid @RequestBody CommuteStatusRequest request) {
        log.info("profile_commute_status_requested userId={}, status={}", shortId(userId), request.status());
        return ApiResponse.ok(profileService.updateCommuteStatus(userId, request.status()));
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 128) String avatarKey,
            @NotEmpty List<@NotBlank @Size(max = 32) String> interestTags,
            @Size(max = 64) String nickname,
            @Size(max = 32) String regionCode,
            @Size(max = 64) String regionName
    ) {
    }

    public record CommuteStatusRequest(@NotBlank @Size(max = 64) String status) {
    }

    private String shortId(String userId) {
        return userId.length() <= 8 ? userId : userId.substring(0, 8);
    }
}
