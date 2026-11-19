package com.veryal.domain.moment;

import java.time.Instant;
import java.util.List;

/**
 * 朋友圈实时事件：点赞、评论、回复。
 *
 * @param recipientIds 需要接收通知的用户；不包含操作者本人
 */
public record MomentEvent(String eventId, MomentEventType type, long momentId, long actorId,
                          Long commentId, List<Long> recipientIds, Instant at) {

    public MomentEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (type == null || at == null) {
            throw new IllegalArgumentException("type/at must not be null");
        }
        if (momentId <= 0 || actorId <= 0) {
            throw new IllegalArgumentException("moment event ids must be positive");
        }
        recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
    }
}
