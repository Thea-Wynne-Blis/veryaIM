package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.UserSocialService;

/**
 * 用户搜索结果。
 */
public record UserSearchResponse(
                                 @Schema(description = "用户 ID")
                                 long userId,
                                 @Schema(description = "登录用户名")
                                 String username,
                                 @Schema(description = "昵称")
                                 String nickname,
                                 @Schema(description = "头像地址")
                                 String avatar
) {

    public static UserSearchResponse from(UserSocialService.UserSearchView view) {
        return new UserSearchResponse(view.userId(), view.username(), view.nickname(),
                view.avatar());
    }
}
