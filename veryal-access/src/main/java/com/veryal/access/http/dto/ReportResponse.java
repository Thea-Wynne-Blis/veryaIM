package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.report.ReportApplicationService;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 举报记录。
 */
public record ReportResponse(
                                 long reportId,
                                 @Schema(description = "举报对象类型：USER / GROUP / MESSAGE")
                                 String targetType,
                                 @Schema(description = "举报对象 ID")
                                 String targetId,
                                 @Schema(description = "原因说明")
                                 String reason,
                                 String detail,
                                 @Schema(description = "状态")
                                 String status,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static ReportResponse from(ReportApplicationService.ReportView view) {
        return new ReportResponse(view.id(), view.targetType(), view.targetId(), view.reason(),
                view.detail(), view.status(), view.createdAt());
    }
}
