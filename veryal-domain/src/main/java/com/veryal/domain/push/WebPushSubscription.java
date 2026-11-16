package com.veryal.domain.push;

import java.time.Instant;

/**
 * 浏览器 Web Push 订阅。
 *
 * <p>{@code endpoint} 由浏览器提供；{@code p256dh}、{@code auth} 用于 RFC 8291 消息加密。</p>
 */
public record WebPushSubscription(long id, long userId, String endpoint, String endpointHash,
                                  String p256dh, String auth, String userAgent, boolean enabled,
                                  Instant createdAt, Instant updatedAt) {

    public WebPushSubscription {
        if (id <= 0 || userId <= 0) {
            throw new IllegalArgumentException("web push ids must be positive");
        }
        if (endpoint == null || endpoint.isBlank() || endpointHash == null
                || endpointHash.length() != 64) {
            throw new IllegalArgumentException("invalid web push endpoint");
        }
        if (p256dh == null || p256dh.isBlank() || auth == null || auth.isBlank()) {
            throw new IllegalArgumentException("invalid web push keys");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }
}
