package com.veryal.access.http.dto;

import com.veryal.application.moment.MomentView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 朋友圈动态。
 */
public record MomentResponse(
        @Schema(description = "动态 ID") long id,
        MomentAuthorResponse author,
        @Schema(description = "正文") String content,
        @Schema(description = "可见范围") String visibility,
        List<MomentMediaResponse> media,
        @Schema(description = "点赞数") int likeCount,
        @Schema(description = "评论数") int commentCount,
        @Schema(description = "最近点赞人，最多 8 个") List<MomentAuthorResponse> likers,
        @Schema(description = "最早评论预览，最多 5 条") List<MomentCommentResponse> comments,
        @Schema(description = "当前用户是否已点赞") boolean liked,
        @Schema(description = "是否为当前用户发布") boolean mine,
        @Schema(description = "是否被运营下架") boolean hidden,
        Instant createdAt
) {

    public static MomentResponse from(MomentView view) {
        return new MomentResponse(view.id(), MomentAuthorResponse.from(view.author()),
                view.content(), view.visibility(),
                view.media().stream().map(MomentMediaResponse::from).toList(),
                view.likeCount(), view.commentCount(),
                view.likers().stream().map(MomentAuthorResponse::from).toList(),
                view.comments().stream().map(MomentCommentResponse::from).toList(),
                view.liked(), view.mine(), view.hidden(), view.createdAt());
    }
}
