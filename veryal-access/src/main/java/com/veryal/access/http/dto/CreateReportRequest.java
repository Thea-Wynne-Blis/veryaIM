package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 提交举报。
 */
public record CreateReportRequest(
                                 @Schema(description = "举报对象类型：USER / GROUP / MESSAGE")
                                 @NotBlank String targetType,
                                 @Schema(description = "举报对象 ID")
                                 @NotBlank String targetId,
                                 @Schema(description = "原因说明")
                                 @NotBlank @Size(max = 64) String reason,
                                 @Size(max = 512) String detail
) {
}
