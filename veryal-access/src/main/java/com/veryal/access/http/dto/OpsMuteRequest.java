package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 运营禁言用户。
 */
public record OpsMuteRequest(
                                 @Schema(description = "时长（分钟）")
                                 Integer minutes,
                                 @Schema(description = "原因说明")
                                 String reason
) {
}
