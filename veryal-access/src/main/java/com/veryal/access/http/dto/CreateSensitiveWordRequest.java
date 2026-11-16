package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 新增敏感词。
 */
public record CreateSensitiveWordRequest(
                                 @Schema(description = "敏感词")
                                 @NotBlank @Size(max = 64) String word
) {
}
