package com.veryal.access.http.dto;

import com.veryal.application.identity.AuthResult;

/**
 * 普通用户登录响应；scope=USER，前端进入 IM 页面。
 */
public record AuthResponse(String token, long expiresInSeconds, String deviceId, UserResponse user,
                           String scope, String role) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token(), result.expiresInSeconds(), result.deviceId(),
                UserResponse.from(result.user()), "USER", "USER");
    }
}
