package com.veryal.domain.report;

import java.util.List;

/**
 * 举报仓储。
 */
public interface ReportRepository {

    void save(Report report);

    List<Report> listByReporter(long reporterId, int limit);
}
