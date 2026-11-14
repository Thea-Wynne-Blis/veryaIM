package com.veryal.access.http.dto;

import com.veryal.application.identity.UserProfile;

import java.time.Instant;

/**
 * 用户资料响应。
 */
public record UserResponse(long userId, String username, String nickname, String avatar, Instant createdAt) {

    public static UserResponse from(UserProfile profile) {
        return new UserResponse(profile.userId(), profile.username(), profile.nickname(), profile.avatar(),
                profile.createdAt());
    }
}
