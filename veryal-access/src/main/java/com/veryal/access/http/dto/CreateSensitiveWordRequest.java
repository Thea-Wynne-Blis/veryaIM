package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增敏感词。
 */
public record CreateSensitiveWordRequest(
        @NotBlank @Size(max = 64) String word) {
}
