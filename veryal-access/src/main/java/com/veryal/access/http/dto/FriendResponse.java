package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.friend.FriendView;

import io.swagger.v3.oas.annotations.media.Schema;
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
                                 @Schema(description = "好友备注名")
                                 String remark,
                                 boolean online,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static FriendResponse from(FriendView view) {
        return new FriendResponse(view.friendId(), view.nickname(), view.avatar(), view.remark(),
                view.online(), view.createdAt());
    }
}
