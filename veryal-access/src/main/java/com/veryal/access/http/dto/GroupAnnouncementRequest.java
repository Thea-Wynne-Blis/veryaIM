package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新群公告。
 */
public record GroupAnnouncementRequest(
                                 @Schema(description = "群公告内容")
                                 @Size(max = 2000) String announcement
) {
}
