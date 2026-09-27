package com.glint.server.common.profile;

import java.util.List;

public final class ProfileContracts {

    private ProfileContracts() {
    }

    public record AnonymousProfile(String profileId, String avatarKey, List<String> interestTags,
                                   String commuteStatus, boolean isVirtual, String nickname,
                                   String regionCode, String regionName, String profileVisibility) {
    }
}
