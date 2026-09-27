package com.glint.server.biz.profile;

import com.glint.server.common.profile.ProfileContracts.AnonymousProfile;
import com.glint.server.dal.profile.ProfileEntity;
import com.glint.server.dal.profile.ProfileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private final ProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public ProfileService(ProfileRepository profileRepository, ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    public AnonymousProfile save(String userId, String avatarKey, List<String> interestTags,
                                 String nickname, String regionCode, String regionName) {
        String commuteStatus = profileRepository.findById(userId).map(ProfileEntity::getCommuteStatus).orElse("idle");
        profileRepository.save(ProfileEntity.of(userId, avatarKey, json(interestTags), commuteStatus,
                blankToNull(nickname), blankToNull(regionCode), blankToNull(regionName)));
        log.info("profile_saved userId={}, tagCount={}, hasNickname={}, hasRegion={}", shortId(userId), interestTags.size(),
                nickname != null && !nickname.isBlank(), regionName != null && !regionName.isBlank());
        return new AnonymousProfile(userId, avatarKey, List.copyOf(interestTags), commuteStatus, false,
                blankToNull(nickname), blankToNull(regionCode), blankToNull(regionName), "MATCH_ONLY");
    }

    public AnonymousProfile current(String userId) {
        return profileRepository.findById(userId)
                .map(this::contract)
                .orElse(new AnonymousProfile(userId, "aurora", List.of(), "idle", false, null, null, null, "MATCH_ONLY"));
    }

    /** 由出发/结束通勤流程更新实时状态，避免用户手工伪造通勤状态。 */
    public AnonymousProfile updateCommuteStatus(String userId, String commuteStatus) {
        ProfileEntity profile = profileRepository.findById(userId).orElseGet(() ->
                ProfileEntity.of(userId, "aurora", json(List.of()), commuteStatus, null, null, null));
        profile.updateCommuteStatus(commuteStatus);
        AnonymousProfile result = contract(profileRepository.save(profile));
        log.info("profile_commute_status_updated userId={}, status={}", shortId(userId), commuteStatus);
        return result;
    }

    private AnonymousProfile contract(ProfileEntity profile) {
        return new AnonymousProfile(profile.getUserId(), profile.getAvatarKey(), tags(profile.getInterestTags()),
                profile.getCommuteStatus(), profile.isVirtual(), profile.getNickname(), profile.getRegionCode(),
                profile.getRegionName(), profile.getProfileVisibility());
    }

    private String json(List<String> tags) {
        try { return objectMapper.writeValueAsString(tags); }
        catch (Exception exception) { throw new IllegalArgumentException("兴趣标签格式无效", exception); }
    }
    private List<String> tags(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("资料标签数据无效", exception); }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String shortId(String userId) {
        return userId.length() <= 8 ? userId : userId.substring(0, 8);
    }
}
