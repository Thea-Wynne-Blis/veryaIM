package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 新建笔记。正文为 Markdown 原文，标题可为空，服务端从正文首行提取。
 */
public record CreateNoteRequest(
        @Schema(description = "标题，最多 200 字；留空时从正文首行提取")
        @Size(max = 200) String title,
        @Schema(description = "Markdown 正文，最多 200000 字")
        @Size(max = 200000) String content
) {
}
