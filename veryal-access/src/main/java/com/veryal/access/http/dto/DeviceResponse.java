package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.DeviceInfo;

/**
 * 设备响应。
 */
public record DeviceResponse(
                                 @Schema(description = "设备唯一标识")
                                 String deviceId,
                                 @Schema(description = "设备平台：IOS / ANDROID / WEB / LINUX / WINDOWS")
                                 String platform,
                                 @Schema(description = "设备名称，用于设备列表展示")
                                 String deviceName,
                                 java.time.Instant lastLoginAt
) {

    public static DeviceResponse from(DeviceInfo info) {
        return new DeviceResponse(info.deviceId(), info.platform().name(), info.deviceName(), info.lastLoginAt());
    }
}
