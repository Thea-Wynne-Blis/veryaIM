package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 翻译请求。目标语言使用百度翻译语言代码，缺省 en。
 */
public record TranslateRequest(
        @Schema(description = "待翻译文本")
        @NotBlank @Size(max = 2000) String text,
        @Schema(description = "目标语言代码，例如 en / zh / jp / kor", example = "en")
        @Size(max = 16) String to
) {
}
