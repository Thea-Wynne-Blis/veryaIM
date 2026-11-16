package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.platform.PlatformAdminService;

/**
 * 平台登录响应。scope=PLATFORM 时前端进入平台管理页。
 */
public record PlatformAuthResponse(
                                 @Schema(description = "访问令牌（JWT）")
                                 String token,
                                 @Schema(description = "令牌有效期（秒）")
                                 long expiresInSeconds,
                                 @Schema(description = "刷新令牌")
                                 String refreshToken,
                                 long refreshExpiresInSeconds,
                                 @Schema(description = "令牌作用域：USER 用户端 / PLATFORM 运营端")
                                 String scope,
                                 @Schema(description = "角色")
                                 String role,
                                 @Schema(description = "管理员 ID")
                                 long adminId,
                                 @Schema(description = "登录用户名")
                                 String username,
                                 String displayName
) {

    public static PlatformAuthResponse from(PlatformAdminService.PlatformAuthResult result) {
        return new PlatformAuthResponse(result.token(), result.expiresInSeconds(),
                result.refreshToken(), result.refreshExpiresInSeconds(), "PLATFORM",
                result.principal().role().name(), result.principal().adminId(),
                result.principal().username(), result.displayName());
    }
}
