package com.veryal.access.http.dto;

import com.veryal.application.moment.MomentNoticeView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 朋友圈互动通知。
 */
public record MomentNoticeResponse(
        @Schema(description = "通知 ID") long id,
        @Schema(description = "LIKE / COMMENT / REPLY") String type,
        MomentAuthorResponse actor,
        @Schema(description = "动态 ID") long momentId,
        @Schema(description = "评论 ID，点赞时为 null") Long commentId,
        @Schema(description = "动态正文摘要") String momentPreview,
        @Schema(description = "是否已读") boolean read,
        Instant createdAt
) {

    public static MomentNoticeResponse from(MomentNoticeView view) {
        return new MomentNoticeResponse(view.id(), view.type(),
                MomentAuthorResponse.from(view.actor()), view.momentId(), view.commentId(),
                view.momentPreview(), view.read(), view.createdAt());
    }
}
