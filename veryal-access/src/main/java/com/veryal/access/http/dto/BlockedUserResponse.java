package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.friend.BlockedUserView;

/**
 * 黑名单用户响应。
 */
public record BlockedUserResponse(
                                 @Schema(description = "用户 ID")
                                 long userId,
                                 @Schema(description = "昵称")
                                 String nickname,
                                 @Schema(description = "头像地址")
                                 String avatar
) {

    public static BlockedUserResponse from(BlockedUserView view) {
        return new BlockedUserResponse(view.userId(), view.nickname(), view.avatar());
    }
}
