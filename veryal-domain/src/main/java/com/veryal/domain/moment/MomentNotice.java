package com.veryal.domain.moment;

import java.time.Instant;

/**
 * 朋友圈互动通知：谁赞了/评论了哪条动态。
 */
public record MomentNotice(long id, long userId, long actorId, MomentNoticeType type,
                           long momentId, Long commentId, Instant readAt, Instant createdAt) {

    public MomentNotice {
        if (id <= 0 || userId <= 0 || actorId <= 0 || momentId <= 0) {
            throw new IllegalArgumentException("moment notice ids must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    public boolean unread() {
        return readAt == null;
    }
}
