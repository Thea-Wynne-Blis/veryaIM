package com.veryal.application.ops;

import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.report.ReportRepository;
import com.veryal.domain.report.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运营概览。
 */
@Service
@RequiredArgsConstructor
public class OpsDashboardService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        return new DashboardView(
                userRepository.count(),
                groupRepository.count(),
                messageRepository.countAll(),
                reportRepository.countByStatus(ReportStatus.PENDING));
    }

    public record DashboardView(long users, long groups, long messages, long pendingReports) {
    }
}
