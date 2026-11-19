package com.veryal.application.moment;

import java.time.Instant;

/**
 * 评论视图。{@code replyToName} 用于渲染「回复某人」。
 */
public record MomentCommentView(long id, MomentAuthorView author, String content,
                                Long replyToId, Long replyToUserId, String replyToName,
                                Instant createdAt, boolean mine) {
}
