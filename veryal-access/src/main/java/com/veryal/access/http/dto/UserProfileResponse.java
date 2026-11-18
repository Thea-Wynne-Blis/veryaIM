package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.UserSocialService;

import java.time.Instant;

/**
 * 用户主页资料。
 */
public record UserProfileResponse(
                                 @Schema(description = "用户 ID")
                                 long userId,
                                 @Schema(description = "登录用户名")
                                 String username,
                                 @Schema(description = "昵称")
                                 String nickname,
                                 @Schema(description = "头像地址")
                                 String avatar,
                                 @Schema(description = "所在地区")
                                 String region,
                                 @Schema(description = "个性签名")
                                 String signature,
                                 @Schema(description = "是否在线（受对方隐私设置约束）")
                                 boolean online,
                                 @Schema(description = "注册时间（UTC）")
                                 Instant createdAt,
                                 @Schema(description = "是否是好友")
                                 boolean friend,
                                 @Schema(description = "成为好友的时间（UTC），从 im_friend.created_at 读取，非好友为 null")
                                 Instant friendSince,
                                 @Schema(description = "我对该好友的备注名，非好友为 null")
                                 String remark,
                                 @Schema(description = "我已发出、对方未处理的好友申请")
                                 boolean pendingOutgoing,
                                 @Schema(description = "对方已发出、我未处理的好友申请")
                                 boolean pendingIncoming
) {

    public static UserProfileResponse from(UserSocialService.UserProfileView view) {
        return new UserProfileResponse(view.userId(), view.username(), view.nickname(),
                view.avatar(), view.region(), view.signature(), view.online(), view.createdAt(),
                view.friend(), view.friendSince(), view.remark(), view.pendingOutgoing(),
                view.pendingIncoming());
    }
}
