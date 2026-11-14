package com.veryal.application.identity;

import com.veryal.domain.device.Device;
import com.veryal.domain.device.Platform;

import java.time.Instant;

/**
 * 设备信息视图。
 */
public record DeviceInfo(String deviceId, Platform platform, String deviceName, Instant lastLoginAt) {

    public static DeviceInfo from(Device device) {
        return new DeviceInfo(device.deviceId(), device.platform(), device.deviceName(), device.lastLoginAt());
    }
}
