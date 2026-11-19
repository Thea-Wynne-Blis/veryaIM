package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 修改好友可见时间范围。
 */
public record UpdateMomentSettingsRequest(
        @Schema(description = "0 全部，3 / 30 / 180 天") int visibleDays
) {
}
