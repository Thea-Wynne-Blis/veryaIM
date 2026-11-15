package com.veryal.domain.push;

import java.time.Instant;

/**
 * 用户设备推送 token。
 */
public record PushDevice(long id, long userId, String deviceId, PushPlatform platform,
                         String token, boolean enabled, Instant createdAt, Instant updatedAt) {
}
