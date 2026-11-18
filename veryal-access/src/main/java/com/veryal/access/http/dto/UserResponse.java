package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.UserProfile;

import java.time.Instant;

/**
 * 用户基础资料响应。
 */
public record UserResponse(
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
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static UserResponse from(UserProfile profile) {
        return new UserResponse(profile.userId(), profile.username(), profile.nickname(),
                profile.avatar(), profile.region(), profile.signature(), profile.createdAt());
    }
}
