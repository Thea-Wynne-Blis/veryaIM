package com.veryal.application.identity;

import com.veryal.domain.device.Device;
import com.veryal.domain.device.Platform;

import java.time.Instant;

/**
 * 设备信息视图。同平台同名的历史重复记录会合并为一条，带上记录条数与当前设备标记。
 */
public record DeviceInfo(String deviceId, Platform platform, String deviceName, Instant lastLoginAt,
                         int deviceCount, boolean current) {

    public static DeviceInfo from(Device device, int deviceCount, boolean current) {
        return new DeviceInfo(device.deviceId(), device.platform(), device.deviceName(),
                device.lastLoginAt(), deviceCount, current);
    }
}
