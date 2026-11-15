package com.veryal.domain.push;

import java.util.Map;

/**
 * 离线推送消息（平台无关）。
 */
public record OfflinePushMessage(long userId, String title, String body, Map<String, String> data) {

    public OfflinePushMessage {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
