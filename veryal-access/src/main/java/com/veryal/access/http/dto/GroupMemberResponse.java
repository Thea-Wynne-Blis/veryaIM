package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 群成员列表项：昵称、角色与禁言状态。
 */
public record GroupMemberResponse(
        @Schema(description = "用户 ID")
        long userId,
        @Schema(description = "昵称")
        String nickname,
        @Schema(description = "好友备注，没设为 null；展示名优先用它")
        String remark,
        @Schema(description = "头像地址")
        String avatar,
        @Schema(description = "是否在线（受对方隐私设置限制）")
        boolean online,
        @Schema(description = "群内角色：OWNER / ADMIN / MEMBER")
        String role,
        @Schema(description = "限时禁言到期时间；永久禁言为 null")
        Instant mutedUntil,
        @Schema(description = "是否永久禁言")
        boolean mutedPermanently
) {
}
