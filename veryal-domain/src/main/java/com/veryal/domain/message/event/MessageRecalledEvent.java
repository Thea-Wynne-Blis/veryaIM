package com.veryal.domain.message.event;

import java.time.Instant;
import java.util.List;

/**
 * 消息撤回事件：撤回落库后写入 Outbox，由各 Gateway 广播 MESSAGE_RECALL。
 */
public record MessageRecalledEvent(String eventId, long tenantId, String conversationId,
                                   long messageId, long senderId, String senderDeviceId, long seq,
                                   Instant recalledAt, List<Long> memberUserIds) {

    public MessageRecalledEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (tenantId <= 0 || messageId <= 0 || senderId <= 0 || seq <= 0) {
            throw new IllegalArgumentException("invalid recall event ids/sequence");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (recalledAt == null) {
            throw new IllegalArgumentException("recalledAt must not be null");
        }
        memberUserIds = memberUserIds == null ? List.of() : List.copyOf(memberUserIds);
    }
}
