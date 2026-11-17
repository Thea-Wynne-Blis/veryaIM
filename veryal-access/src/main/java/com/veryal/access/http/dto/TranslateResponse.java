package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 翻译结果。
 */
public record TranslateResponse(
        @Schema(description = "识别出的源语言代码，auto 表示自动识别")
        String from,
        @Schema(description = "目标语言代码")
        String to,
        @Schema(description = "译文")
        String text
) {
}
