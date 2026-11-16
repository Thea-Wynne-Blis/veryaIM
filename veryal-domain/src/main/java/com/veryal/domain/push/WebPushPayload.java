package com.veryal.domain.push;

import java.util.Map;

/**
 * Web Push 待发送内容。
 */
public record WebPushPayload(String title, String body, Map<String, String> data) {

    public WebPushPayload {
        if (title == null || title.isBlank()) {
            title = "VERYAL";
        }
        if (body == null) {
            body = "";
        }
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
