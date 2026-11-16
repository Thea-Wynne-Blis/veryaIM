package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.push.PushDeviceService;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 推送设备响应。
 */
public record PushDeviceResponse(
                                 @Schema(description = "设备唯一标识")
                                 String deviceId,
                                 @Schema(description = "设备平台：IOS / ANDROID / WEB / LINUX / WINDOWS")
                                 String platform,
                                 @Schema(description = "访问令牌（JWT）")
                                 String token,
                                 @Schema(description = "是否启用")
                                 boolean enabled,
                                 @Schema(description = "更新时间（UTC）")
                                 Instant updatedAt
) {

    public static PushDeviceResponse from(PushDeviceService.PushDeviceView view) {
        return new PushDeviceResponse(view.deviceId(), view.platform(), view.token(),
                view.enabled(), view.updatedAt());
    }
}
