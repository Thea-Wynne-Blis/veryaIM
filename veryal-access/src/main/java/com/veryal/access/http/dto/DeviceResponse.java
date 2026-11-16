package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.identity.DeviceInfo;

/**
 * 设备响应。同平台同名的历史记录已合并，deviceCount 为合并前的记录条数。
 */
public record DeviceResponse(
                                 @Schema(description = "设备唯一标识")
                                 String deviceId,
                                 @Schema(description = "设备平台：IOS / ANDROID / WEB / LINUX / WINDOWS")
                                 String platform,
                                 @Schema(description = "设备名称，用于设备列表展示")
                                 String deviceName,
                                 @Schema(description = "最近登录时间")
                                 java.time.Instant lastLoginAt,
                                 @Schema(description = "同平台同名的登录记录条数")
                                 int deviceCount,
                                 @Schema(description = "是否为当前登录设备")
                                 boolean current
) {

    public static DeviceResponse from(DeviceInfo info) {
        return new DeviceResponse(info.deviceId(), info.platform().name(), info.deviceName(),
                info.lastLoginAt(), info.deviceCount(), info.current());
    }
}
