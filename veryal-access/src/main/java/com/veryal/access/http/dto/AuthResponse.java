package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.AuthResult;

/**
 * 普通用户登录响应；scope=USER，前端进入 IM 页面。
 */
public record AuthResponse(
                                 @Schema(description = "访问令牌（JWT）")
                                 String token,
                                 @Schema(description = "令牌有效期（秒）")
                                 long expiresInSeconds,
                                 @Schema(description = "设备唯一标识")
                                 String deviceId,
                                 UserResponse user,
                                 @Schema(description = "令牌作用域：USER 用户端 / PLATFORM 运营端")
                                 String scope,
                                 @Schema(description = "角色")
                                 String role
) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token(), result.expiresInSeconds(), result.deviceId(),
                UserResponse.from(result.user()), "USER", "USER");
    }
}
