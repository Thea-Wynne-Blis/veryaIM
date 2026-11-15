package com.veryal.domain.push;

import java.util.Map;

/**
 * 离线推送消息，包含目标设备 token。
 */
public record OfflinePushMessage(long userId, String deviceId, PushPlatform platform, String token,
                                 String title, String body, Map<String, String> data) {

    public OfflinePushMessage {
        if (userId <= 0 || deviceId == null || deviceId.isBlank() || token == null
                || token.isBlank() || platform == null || title == null || body == null) {
            throw new IllegalArgumentException("invalid offline push message");
        }
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
