package com.veryal.domain.conversation;

import java.time.Instant;

/**
 * 会话聚合根。
 */
public record Conversation(String id, ConversationType type, Instant createdAt, Instant updatedAt) {

    public Conversation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
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

    public static Conversation createPrivate(long userA, long userB, Instant now) {
        return new Conversation(ConversationIds.privateId(userA, userB),
                ConversationType.PRIVATE, now, now);
    }

    public static Conversation createGroup(long snowflakeId, Instant now) {
        return new Conversation(ConversationIds.groupId(snowflakeId),
                ConversationType.GROUP, now, now);
    }

    public Conversation touch(Instant now) {
        return new Conversation(id, type, createdAt, now);
    }
}
