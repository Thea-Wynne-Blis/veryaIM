package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交举报。
 */
public record CreateReportRequest(@NotBlank String targetType, @NotBlank String targetId,
                                  @NotBlank @Size(max = 64) String reason,
                                  @Size(max = 512) String detail) {
}
