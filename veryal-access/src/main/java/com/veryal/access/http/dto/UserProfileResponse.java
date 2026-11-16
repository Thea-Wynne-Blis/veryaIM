package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.UserSocialService;

import io.swagger.v3.oas.annotations.media.Schema;
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
                                 boolean online,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static UserProfileResponse from(UserSocialService.UserProfileView view) {
        return new UserProfileResponse(view.userId(), view.username(), view.nickname(),
                view.avatar(), view.online(), view.createdAt());
    }
}
