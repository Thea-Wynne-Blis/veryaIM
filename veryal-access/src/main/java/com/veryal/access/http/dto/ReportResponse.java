package com.veryal.access.http.dto;

import com.veryal.application.report.ReportApplicationService;

import java.time.Instant;

/**
 * 举报记录。
 */
public record ReportResponse(long reportId, String targetType, String targetId, String reason,
                             String detail, String status, Instant createdAt) {

    public static ReportResponse from(ReportApplicationService.ReportView view) {
        return new ReportResponse(view.id(), view.targetType(), view.targetId(), view.reason(),
                view.detail(), view.status(), view.createdAt());
    }
}
