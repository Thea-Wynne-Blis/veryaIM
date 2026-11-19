package com.veryal.application.moment;

import java.time.Instant;

/**
 * 互动通知视图。
 *
 * @param momentPreview 动态正文摘要，便于通知列表直接展示
 */
public record MomentNoticeView(long id, String type, MomentAuthorView actor, long momentId,
                               Long commentId, String momentPreview, boolean read,
                               Instant createdAt) {
}
