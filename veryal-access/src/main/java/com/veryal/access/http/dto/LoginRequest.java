package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。deviceId 由客户端生成并持久化，用于多端识别。
 */
public record LoginRequest(
                                 @Schema(description = "登录用户名")
                                 @NotBlank String username,
                                 @Schema(description = "密码（明文传输需 HTTPS）")
                                 @NotBlank String password,
                                 @Schema(description = "设备唯一标识")
                                 @NotBlank @Size(max = 64) String deviceId,
                                 @Schema(description = "设备平台：IOS / ANDROID / WEB / LINUX / WINDOWS")
                                 @Size(max = 32) String platform,
                                 @Schema(description = "设备名称，用于设备列表展示")
                                 @Size(max = 128) String deviceName
) {
}
