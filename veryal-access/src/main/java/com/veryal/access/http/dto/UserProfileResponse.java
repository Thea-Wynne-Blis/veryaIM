package com.veryal.access.http.dto;

import com.veryal.application.identity.UserSocialService;

import java.time.Instant;

/**
 * 用户主页资料。
 */
public record UserProfileResponse(long userId, String username, String nickname, String avatar,
                                  boolean online, Instant createdAt) {

    public static UserProfileResponse from(UserSocialService.UserProfileView view) {
        return new UserProfileResponse(view.userId(), view.username(), view.nickname(),
                view.avatar(), view.online(), view.createdAt());
    }
}
