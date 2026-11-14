package com.veryal.domain.device;

import java.time.Instant;

/**
 * 设备实体。一个用户可在多个设备同时在线。
 */
public record Device(long id, long userId, String deviceId, Platform platform, String deviceName,
                     Instant lastLoginAt, Instant createdAt) {

    public Device {
        if (id <= 0) {
            throw new IllegalArgumentException("device id must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (platform == null) {
            platform = Platform.UNKNOWN;
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    public static Device bind(long id, long userId, String deviceId, Platform platform,
                              String deviceName, Instant now) {
        return new Device(id, userId, deviceId, platform, deviceName, now, now);
    }

    public Device loginAt(Instant now) {
        return new Device(id, userId, deviceId, platform, deviceName, now, createdAt);
    }

    public Device rename(String newDeviceName) {
        return new Device(id, userId, deviceId, platform, newDeviceName, lastLoginAt, createdAt);
    }
}
