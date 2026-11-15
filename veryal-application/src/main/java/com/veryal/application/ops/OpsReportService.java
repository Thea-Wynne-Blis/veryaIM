package com.veryal.application.ops;

import com.veryal.application.platform.PlatformAuditRecorder;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.report.Report;
import com.veryal.domain.report.ReportRepository;
import com.veryal.domain.report.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运营后台举报处理。
 */
@Service
@RequiredArgsConstructor
public class OpsReportService {

    private final ReportRepository reportRepository;
    private final PlatformAuditRecorder auditRecorder;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public List<OpsReportView> list(String status, int limit) {
        ReportStatus filter = status == null || status.isBlank()
                ? null : parseStatus(status);
        return reportRepository.list(filter, Math.max(1, Math.min(limit, 200))).stream()
                .map(OpsReportView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OpsReportView get(long reportId) {
        return OpsReportView.from(require(reportId));
    }

    @Transactional
    public OpsReportView handle(long adminId, long reportId, String status, String note) {
        ReportStatus target = parseStatus(status);
        if (target != ReportStatus.HANDLED && target != ReportStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "status must be HANDLED or REJECTED");
        }
        Report updated = require(reportId).handle(target, adminId, timeProvider.now());
        reportRepository.update(updated);
        auditRecorder.record(adminId, "OPS_REPORT_HANDLE", "report", Long.toString(reportId),
                "status=" + target + ",note=" + (note == null ? "" : note));
        return OpsReportView.from(updated);
    }

    private ReportStatus parseStatus(String status) {
        try {
            return ReportStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid report status");
        }
    }

    private Report require(long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "report not found"));
    }

    public record OpsReportView(long reportId, long reporterId, String targetType, String targetId,
                                String reason, String detail, String status, Long handledBy,
                                java.time.Instant handledAt, java.time.Instant createdAt) {

        static OpsReportView from(Report report) {
            return new OpsReportView(report.id(), report.reporterId(), report.targetType().name(),
                    report.targetId(), report.reason(), report.detail(), report.status().name(),
                    report.handledBy(), report.handledAt(), report.createdAt());
        }
    }
}
