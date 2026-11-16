package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 运营处理举报。
 */
public record OpsHandleReportRequest(
                                 @Schema(description = "状态")
                                 String status,
                                 String note
) {
}
