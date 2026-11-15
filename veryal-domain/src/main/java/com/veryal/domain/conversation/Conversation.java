package com.veryal.domain.conversation;

import java.time.Instant;

/**
 * 会话聚合根。
 */
public record Conversation(String id, long tenantId, ConversationType type, Instant createdAt, Instant updatedAt) {

    public Conversation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenant id must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("conversation type must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public static Conversation createPrivate(long tenantId, long userA, long userB, Instant now) {
        return new Conversation(ConversationIds.privateId(userA, userB), tenantId,
                ConversationType.PRIVATE, now, now);
    }

    public static Conversation createGroup(long tenantId, long snowflakeId, Instant now) {
        return new Conversation(ConversationIds.groupId(snowflakeId), tenantId,
                ConversationType.GROUP, now, now);
    }

    public Conversation touch(Instant now) {
        return new Conversation(id, tenantId, type, createdAt, now);
    }
}
