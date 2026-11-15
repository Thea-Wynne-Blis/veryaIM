package com.veryal.access.http.dto;

import com.veryal.application.identity.UserSocialService;

/**
 * 用户搜索结果。
 */
public record UserSearchResponse(long userId, String username, String nickname, String avatar) {

    public static UserSearchResponse from(UserSocialService.UserSearchView view) {
        return new UserSearchResponse(view.userId(), view.username(), view.nickname(),
                view.avatar());
    }
}
