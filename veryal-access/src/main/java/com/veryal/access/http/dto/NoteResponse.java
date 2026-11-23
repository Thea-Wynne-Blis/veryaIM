package com.veryal.access.http.dto;

import com.veryal.application.note.NoteView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 笔记完整内容。
 */
public record NoteResponse(
        @Schema(description = "笔记 ID")
        long id,
        @Schema(description = "标题")
        String title,
        @Schema(description = "Markdown 正文")
        String content,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "最近更新时间")
        Instant updatedAt
) {

    public static NoteResponse from(NoteView view) {
        return new NoteResponse(view.id(), view.title(), view.content(),
                view.createdAt(), view.updatedAt());
    }
}
