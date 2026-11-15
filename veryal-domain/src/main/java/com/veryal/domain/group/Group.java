package com.veryal.domain.group;

import java.time.Instant;

/**
 * 群聚合根。群 ID 与群会话 ID 保持一致，消息序号按会话维护。
 */
public record Group(String id, String name, long ownerId,
                    Instant createdAt, Instant updatedAt) {

    public static final int MAX_NAME_LENGTH = 128;
    public static final int MAX_MEMBERS = 1000;

    public Group {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("group id must not be blank");
        }
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("invalid group name");
        }
        if (ownerId <= 0) {
            throw new IllegalArgumentException("owner id must be positive");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public static Group create(String id, String name, long ownerId, Instant now) {
        return new Group(id, name, ownerId, now, now);
    }

    public Group rename(String newName, Instant now) {
        return new Group(id, newName, ownerId, createdAt, now);
    }

    public Group transferOwner(long newOwnerId, Instant now) {
        if (newOwnerId <= 0 || newOwnerId == ownerId) {
            throw new IllegalArgumentException("invalid new owner");
        }
        return new Group(id, name, newOwnerId, createdAt, now);
    }
}
