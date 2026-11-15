package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.report.Report;
import com.veryal.domain.report.ReportRepository;
import com.veryal.domain.report.ReportStatus;
import com.veryal.domain.report.ReportTargetType;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.ReportMapper;
import com.veryal.infrastructure.mysql.po.ReportPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 举报 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisReportRepository implements ReportRepository {

    private final ReportMapper reportMapper;

    @Override
    public void save(Report report) {
        ReportPO po = new ReportPO();
        po.id = report.id();
        po.reporterId = report.reporterId();
        po.targetType = report.targetType().name();
        po.targetId = report.targetId();
        po.reason = report.reason();
        po.detail = report.detail();
        po.status = report.status().code();
        po.handledBy = report.handledBy();
        po.handledAt = report.handledAt() == null ? null : SqlTime.toLocal(report.handledAt());
        po.createdAt = SqlTime.toLocal(report.createdAt());
        reportMapper.insert(po);
    }

    @Override
    public List<Report> listByReporter(long reporterId, int limit) {
        return reportMapper.selectList(Wrappers.<ReportPO>lambdaQuery()
                        .eq(ReportPO::getReporterId, reporterId)
                        .orderByDesc(ReportPO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream()
                .map(po -> new Report(po.id, po.reporterId,
                        ReportTargetType.valueOf(po.targetType), po.targetId, po.reason, po.detail,
                        ReportStatus.fromCode(po.status), po.handledBy,
                        po.handledAt == null ? null : SqlTime.toInstant(po.handledAt),
                        SqlTime.toInstant(po.createdAt)))
                .toList();
    }
}
