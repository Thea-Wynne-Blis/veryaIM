package com.veryal.access.http.dto;

import com.veryal.application.identity.AuthResult;

/**
 * 登录响应。
 */
public record AuthResponse(String token, long expiresInSeconds, String deviceId, UserResponse user) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token(), result.expiresInSeconds(), result.deviceId(),
                UserResponse.from(result.user()));
    }
}
