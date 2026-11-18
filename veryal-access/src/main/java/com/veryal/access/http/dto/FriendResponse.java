package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.friend.FriendView;

import java.time.Instant;

/**
 * 好友响应。
 */
public record FriendResponse(
                                 @Schema(description = "好友用户 ID")
                                 long friendId,
                                 @Schema(description = "昵称")
                                 String nickname,
                                 @Schema(description = "头像地址")
                                 String avatar,
                                 @Schema(description = "所在地区")
                                 String region,
                                 @Schema(description = "个性签名")
                                 String signature,
                                 @Schema(description = "好友备注名")
                                 String remark,
                                 boolean online,
                                 @Schema(description = "成为好友的时间（UTC），来自 im_friend.created_at")
                                 Instant createdAt
) {

    public static FriendResponse from(FriendView view) {
        return new FriendResponse(view.friendId(), view.nickname(), view.avatar(),
                view.region(), view.signature(), view.remark(), view.online(), view.createdAt());
    }
}
