package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新隐私设置；字段为空表示保持原值。
 */
public record UpdatePrivacyRequest(
                                 Boolean searchByPhone,
                                 Boolean searchByUsername,
                                 @Schema(description = "加好友策略：REJECT 拒绝 / VERIFY 需验证 / DIRECT 直接通过")
                                 String friendPolicy,
                                 @Schema(description = "是否展示在线状态")
                                 Boolean showOnline,
                                 @Schema(description = "是否发送已读回执")
                                 Boolean readReceipt,
                                 Boolean invitedToGroup
) {
}
