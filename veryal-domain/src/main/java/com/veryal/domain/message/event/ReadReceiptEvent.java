package com.veryal.domain.message.event;

import java.time.Instant;
import java.util.List;

/**
 * 已读回执事件。由读游标推进后写入 Outbox，各 Gateway 广播给会话成员的在线连接。
 */
public record ReadReceiptEvent(String eventId, String conversationId, long readerId,
                               String readerDeviceId, long readSequence, Instant readAt,
                               List<Long> memberUserIds) {

    public ReadReceiptEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (readerId <= 0 || readSequence < 0) {
            throw new IllegalArgumentException("invalid read receipt ids/sequence");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (readAt == null) {
            throw new IllegalArgumentException("readAt must not be null");
        }
        memberUserIds = memberUserIds == null ? List.of() : List.copyOf(memberUserIds);
    }
}
