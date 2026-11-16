package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 注册推送设备。
 */
public record RegisterPushDeviceRequest(
                                 @Schema(description = "设备唯一标识")
                                 @NotBlank @Size(max = 64) String deviceId,
                                 @Schema(description = "设备平台：IOS / ANDROID / WEB / LINUX / WINDOWS")
                                 @NotBlank @Size(max = 16) String platform,
                                 @Schema(description = "访问令牌（JWT）")
                                 @NotBlank @Size(max = 512) String token
) {
}
