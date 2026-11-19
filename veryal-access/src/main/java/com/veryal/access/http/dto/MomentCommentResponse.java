package com.veryal.access.http.dto;

import com.veryal.application.moment.MomentCommentView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 动态评论。
 */
public record MomentCommentResponse(
        @Schema(description = "评论 ID") long id,
        MomentAuthorResponse author,
        @Schema(description = "评论内容") String content,
        @Schema(description = "回复的评论 ID") Long replyToId,
        @Schema(description = "被回复用户 ID") Long replyToUserId,
        @Schema(description = "被回复用户显示名") String replyToName,
        Instant createdAt,
        @Schema(description = "是否为当前用户发表") boolean mine
) {

    public static MomentCommentResponse from(MomentCommentView view) {
        return new MomentCommentResponse(view.id(), MomentAuthorResponse.from(view.author()),
                view.content(), view.replyToId(), view.replyToUserId(), view.replyToName(),
                view.createdAt(), view.mine());
    }
}
