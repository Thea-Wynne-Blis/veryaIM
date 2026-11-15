package com.veryal.domain.report;

import java.util.List;
import java.util.Optional;

/**
 * 举报仓储。
 */
public interface ReportRepository {

    void save(Report report);

    Optional<Report> findById(long reportId);

    List<Report> list(ReportStatus status, int limit);

    List<Report> listByReporter(long reporterId, int limit);

    void update(Report report);

    long countByStatus(ReportStatus status);
}
