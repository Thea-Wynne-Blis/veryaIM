package com.veryal.access.http.dto;

import com.veryal.application.identity.DeviceInfo;

/**
 * 设备响应。
 */
public record DeviceResponse(String deviceId, String platform, String deviceName,
                             java.time.Instant lastLoginAt) {

    public static DeviceResponse from(DeviceInfo info) {
        return new DeviceResponse(info.deviceId(), info.platform().name(), info.deviceName(), info.lastLoginAt());
    }
}
