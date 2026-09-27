package com.glint.server.dal.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "t_profiles")
public class ProfileEntity {
    @Id
    @Column(name = "user_id")
    private String userId;
    @Column(name = "avatar_key", nullable = false)
    private String avatarKey;
    @Column(name = "interest_tags", nullable = false, columnDefinition = "json")
    private String interestTags;
    @Column(name = "commute_status")
    private String commuteStatus;
    @Column(name = "is_virtual", nullable = false)
    private boolean virtual;
    @Column(name = "nickname")
    private String nickname;
    @Column(name = "region_code")
    private String regionCode;
    @Column(name = "region_name")
    private String regionName;
    @Column(name = "profile_visibility", nullable = false)
    private String profileVisibility;

    protected ProfileEntity() { }
    private ProfileEntity(String userId, String avatarKey, String interestTags, String commuteStatus, boolean virtual,
                          String nickname, String regionCode, String regionName, String profileVisibility) {
        this.userId = userId;
        this.avatarKey = avatarKey;
        this.interestTags = interestTags;
        this.commuteStatus = commuteStatus;
        this.virtual = virtual;
        this.nickname = nickname;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.profileVisibility = profileVisibility;
    }
    public static ProfileEntity of(String userId, String avatarKey, String interestTags, String commuteStatus,
                                   String nickname, String regionCode, String regionName) {
        return new ProfileEntity(userId, avatarKey, interestTags, commuteStatus, false, nickname, regionCode,
                regionName, "MATCH_ONLY");
    }
    public String getUserId() { return userId; }
    public String getAvatarKey() { return avatarKey; }
    public String getInterestTags() { return interestTags; }
    public String getCommuteStatus() { return commuteStatus; }
    public boolean isVirtual() { return virtual; }
    public String getNickname() { return nickname; }
    public String getRegionCode() { return regionCode; }
    public String getRegionName() { return regionName; }
    public String getProfileVisibility() { return profileVisibility; }
    /** 仅由通勤流程更新当前状态，资料编辑页不得手动设置。 */
    public void updateCommuteStatus(String commuteStatus) { this.commuteStatus = commuteStatus; }
}
