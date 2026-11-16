package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.identity.UserPrivacy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 用户隐私设置。
 */
public record UserPrivacyResponse(
                                 boolean searchByPhone,
                                 boolean searchByUsername,
                                 @Schema(description = "加好友策略：REJECT 拒绝 / VERIFY 需验证 / DIRECT 直接通过")
                                 String friendPolicy,
                                 @Schema(description = "是否展示在线状态")
                                 boolean showOnline,
                                 @Schema(description = "是否发送已读回执")
                                 boolean readReceipt,
                                 boolean invitedToGroup,
                                 @Schema(description = "更新时间（UTC）")
                                 Instant updatedAt
) {

    public static UserPrivacyResponse from(UserPrivacy privacy) {
        return new UserPrivacyResponse(privacy.searchByPhone(), privacy.searchByUsername(),
                privacy.friendPolicy().name(), privacy.showOnline(), privacy.readReceipt(),
                privacy.invitedToGroup(), privacy.updatedAt());
    }
}
