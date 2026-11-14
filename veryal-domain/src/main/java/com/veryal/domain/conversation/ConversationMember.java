package com.veryal.domain.conversation;

import java.time.Instant;

/**
 * 会话成员，保存用户级读游标与同步游标。
 */
public record ConversationMember(long id, String conversationId, long userId,
                                 long lastReadSeq, long lastSyncSeq, Instant joinedAt) {

    public ConversationMember {
        if (id <= 0) {
            throw new IllegalArgumentException("member id must be positive");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        if (joinedAt == null) {
            throw new IllegalArgumentException("joinedAt must not be null");
        }
    }

    public static ConversationMember join(long id, String conversationId, long userId, Instant now) {
        return new ConversationMember(id, conversationId, userId, 0L, 0L, now);
    }

    /**
     * 读游标只允许前进，避免乱序 ACK 造成游标回退。
     */
    public ConversationMember advanceRead(long seq) {
        long next = Math.max(lastReadSeq, seq);
        return new ConversationMember(id, conversationId, userId, next, lastSyncSeq, joinedAt);
    }

    public ConversationMember advanceSync(long seq) {
        long next = Math.max(lastSyncSeq, seq);
        return new ConversationMember(id, conversationId, userId, lastReadSeq, next, joinedAt);
    }
}
