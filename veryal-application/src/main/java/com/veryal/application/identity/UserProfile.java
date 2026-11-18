package com.veryal.application.identity;

import com.veryal.domain.identity.User;

import java.time.Instant;

/**
 * 对外暴露的用户资料，不包含密码哈希等敏感字段。
 */
public record UserProfile(long userId, String username, String nickname, String avatar,
                          String region, String signature, Instant createdAt) {

    public static UserProfile from(User user) {
        return new UserProfile(user.id(), user.username(), user.nickname(), user.avatar(),
                user.region(), user.signature(), user.createdAt());
    }
}
