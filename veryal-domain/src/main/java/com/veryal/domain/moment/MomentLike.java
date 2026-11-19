package com.veryal.domain.moment;

import java.time.Instant;

/**
 * 点赞记录。同一用户对同一条动态最多一条，主键为 (momentId, userId)。
 */
public record MomentLike(long momentId, long userId, Instant createdAt) {

    public MomentLike {
        if (momentId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("like ids must be positive");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
