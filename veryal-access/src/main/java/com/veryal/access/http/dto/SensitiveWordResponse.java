package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.moderation.SensitiveWord;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 敏感词响应。
 */
public record SensitiveWordResponse(
                                 @Schema(description = "主键")
                                 long id,
                                 @Schema(description = "敏感词")
                                 String word,
                                 @Schema(description = "是否启用")
                                 boolean enabled,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static SensitiveWordResponse from(SensitiveWord word) {
        return new SensitiveWordResponse(word.id(), word.word(), word.enabled(),
                word.createdAt());
    }
}
