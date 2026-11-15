package com.veryal.access.http.dto;

import com.veryal.application.friend.BlockedUserView;

/**
 * 黑名单用户响应。
 */
public record BlockedUserResponse(long userId, String nickname, String avatar) {

    public static BlockedUserResponse from(BlockedUserView view) {
        return new BlockedUserResponse(view.userId(), view.nickname(), view.avatar());
    }
}
