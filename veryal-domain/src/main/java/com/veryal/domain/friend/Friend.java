package com.veryal.domain.friend;

import java.time.Instant;

/**
 * 好友关系（双向各一行）。
 */
public record Friend(long id, long tenantId, long userId, long friendId, String remark,
                     Instant createdAt) {

    public Friend {
        if (id <= 0 || tenantId <= 0 || userId <= 0 || friendId <= 0) {
            throw new IllegalArgumentException("friend ids must be positive");
        }
        if (userId == friendId) {
            throw new IllegalArgumentException("cannot be friend with self");
        }
        if (remark != null && remark.length() > 64) {
            remark = remark.substring(0, 64);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
