package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 成员禁言分钟数，0 表示解除。
 */
public record GroupMemberMuteRequest(
                                 @Schema(description = "时长（分钟）")
                                 @Min(0) @Max(10080) int minutes
) {
}
