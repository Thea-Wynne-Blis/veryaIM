package com.veryal.domain.group;

import java.time.Instant;

/**
 * 群成员。
 */
public record GroupMember(long id, String groupId, long userId, GroupRole role, Instant joinedAt) {

    public GroupMember {
        if (id <= 0) {
            throw new IllegalArgumentException("member id must be positive");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId must not be blank");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (joinedAt == null) {
            throw new IllegalArgumentException("joinedAt must not be null");
        }
    }

    public static GroupMember join(long id, String groupId, long userId, GroupRole role, Instant now) {
        return new GroupMember(id, groupId, userId, role, now);
    }
}
