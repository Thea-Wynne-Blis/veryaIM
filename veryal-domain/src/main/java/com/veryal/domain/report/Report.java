package com.veryal.domain.report;

import java.time.Instant;

/**
 * 用户举报。
 */
public record Report(long id, long reporterId, ReportTargetType targetType, String targetId,
                     String reason, String detail, ReportStatus status, Long handledBy,
                     Instant handledAt, Instant createdAt) {

    public static Report create(long id, long reporterId, ReportTargetType targetType,
                                String targetId, String reason, String detail, Instant now) {
        return new Report(id, reporterId, targetType, targetId, reason, detail,
                ReportStatus.PENDING, null, null, now);
    }

    public Report handle(ReportStatus newStatus, long adminId, Instant now) {
        return new Report(id, reporterId, targetType, targetId, reason, detail, newStatus,
                adminId, now, createdAt);
    }
}
