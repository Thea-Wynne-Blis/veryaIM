package com.veryal.domain.message.outbox;

import java.time.Instant;

/**
 * 待发布的 Outbox 记录。
 */
public record OutboxMessage(String eventId, String aggregateId, String eventType,
                            byte[] payload, Instant createdAt) {

    public OutboxMessage {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
