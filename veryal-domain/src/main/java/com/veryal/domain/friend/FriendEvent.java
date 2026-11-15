package com.veryal.domain.friend;

import java.time.Instant;
import java.util.List;

/**
 * 好友实时事件：申请、接受、拒绝、取消。
 */
public record FriendEvent(String eventId, long tenantId, FriendEventType type, long requestId,
                          long fromUserId, long toUserId, String message, Instant at,
                          List<Long> memberUserIds) {

    public FriendEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (tenantId <= 0 || requestId < 0 || fromUserId <= 0 || toUserId <= 0) {
            throw new IllegalArgumentException("friend event ids must be positive");
        }
        if (type == null || at == null) {
            throw new IllegalArgumentException("type/at must not be null");
        }
        memberUserIds = memberUserIds == null ? List.of() : List.copyOf(memberUserIds);
    }
}
