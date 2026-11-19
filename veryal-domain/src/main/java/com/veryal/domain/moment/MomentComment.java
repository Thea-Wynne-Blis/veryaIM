package com.veryal.domain.moment;

import java.time.Instant;

/**
 * 动态评论。回复某条评论时同时记录被回复的用户，读取时不必再回查父评论。
 */
public record MomentComment(long id, long momentId, long userId, Long replyToId,
                            Long replyToUserId, String content, Instant createdAt,
                            Instant deletedAt) {

    public static final int MAX_CONTENT_LENGTH = 500;

    public MomentComment {
        if (id <= 0 || momentId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("comment ids must be positive");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("comment content must not be blank");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    public boolean deleted() {
        return deletedAt != null;
    }
}
