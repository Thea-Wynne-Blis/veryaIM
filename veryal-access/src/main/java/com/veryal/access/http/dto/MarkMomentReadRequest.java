package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 标记信息流已读到哪一条。
 */
public record MarkMomentReadRequest(
        @Schema(description = "当前看到的最新动态 ID") long momentId
) {
}
