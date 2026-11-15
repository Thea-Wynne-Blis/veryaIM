package com.veryal.access.http.dto;

import com.veryal.application.platform.PlatformAdminService;

/**
 * 平台登录响应。scope=PLATFORM 时前端进入平台管理页。
 */
public record PlatformAuthResponse(String token, long expiresInSeconds, String refreshToken,
                                   long refreshExpiresInSeconds, String scope, String role,
                                   long adminId, String username, String displayName) {

    public static PlatformAuthResponse from(PlatformAdminService.PlatformAuthResult result) {
        return new PlatformAuthResponse(result.token(), result.expiresInSeconds(),
                result.refreshToken(), result.refreshExpiresInSeconds(), "PLATFORM",
                result.principal().role().name(), result.principal().adminId(),
                result.principal().username(), result.displayName());
    }
}
