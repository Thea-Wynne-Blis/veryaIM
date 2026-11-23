package com.veryal.access.http.dto;

import com.veryal.application.note.NoteSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 笔记列表项，只带标题与摘要。
 */
public record NoteSummaryResponse(
        @Schema(description = "笔记 ID")
        long id,
        @Schema(description = "标题")
        String title,
        @Schema(description = "正文纯文本摘要")
        String excerpt,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {

    public static NoteSummaryResponse from(NoteSummaryView view) {
        return new NoteSummaryResponse(view.id(), view.title(), view.excerpt(),
                view.createdAt(), view.updatedAt());
    }
}
