package com.veryal.domain.delivery;

import java.time.Instant;

/**
 * 消息投递记录：某设备确认收到某条消息。
 */
public record DeliveryRecord(long id, long messageId, String conversationId,
                             long userId, String deviceId, long deliveredSequence, Instant deliveredAt) {

    public DeliveryRecord {
        if (id <= 0 || messageId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("delivery ids must be positive");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (deliveredSequence < 0) {
            throw new IllegalArgumentException("deliveredSequence must not be negative");
        }
        if (deliveredAt == null) {
            throw new IllegalArgumentException("deliveredAt must not be null");
        }
    }
}
