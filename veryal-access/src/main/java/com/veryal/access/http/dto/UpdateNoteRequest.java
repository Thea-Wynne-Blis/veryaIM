package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新笔记。字段为空时保留原值，便于只改正文或只改标题。
 */
public record UpdateNoteRequest(
        @Schema(description = "标题，最多 200 字；传空字符串时按正文首行重新生成")
        @Size(max = 200) String title,
        @Schema(description = "Markdown 正文，最多 200000 字")
        @Size(max = 200000) String content
) {
}
