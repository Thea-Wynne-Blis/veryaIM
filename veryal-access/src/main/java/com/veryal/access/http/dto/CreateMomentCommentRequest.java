package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 发表评论或回复。
 */
public record CreateMomentCommentRequest(
        @Schema(description = "评论内容，最多 500 字")
        @Size(max = 500) String content,
        @Schema(description = "回复的评论 ID；为空表示直接评论动态")
        Long replyToCommentId
) {
}
