package com.veryal.access.http.dto;

import com.veryal.application.platform.PlatformAdminService;

/**
 * 平台登录响应。前端根据 scope=PLATFORM 进入平台管理页。
 */
public record PlatformAuthResponse(String token, long expiresInSeconds, String scope, String role,
                                   long adminId, String username, String displayName) {

    public static PlatformAuthResponse from(PlatformAdminService.PlatformAuthResult result) {
        return new PlatformAuthResponse(result.token(), result.expiresInSeconds(), "PLATFORM",
                result.principal().role().name(), result.principal().adminId(),
                result.principal().username(), result.displayName());
    }
}
