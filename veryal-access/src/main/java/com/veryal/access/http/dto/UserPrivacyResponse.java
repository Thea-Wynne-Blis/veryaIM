package com.veryal.access.http.dto;

import com.veryal.domain.identity.UserPrivacy;

import java.time.Instant;

/**
 * 用户隐私设置。
 */
public record UserPrivacyResponse(boolean searchByPhone, boolean searchByUsername,
                                  String friendPolicy, boolean showOnline, boolean readReceipt,
                                  boolean invitedToGroup, Instant updatedAt) {

    public static UserPrivacyResponse from(UserPrivacy privacy) {
        return new UserPrivacyResponse(privacy.searchByPhone(), privacy.searchByUsername(),
                privacy.friendPolicy().name(), privacy.showOnline(), privacy.readReceipt(),
                privacy.invitedToGroup(), privacy.updatedAt());
    }
}
