package com.veryal.application.report;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.report.Report;
import com.veryal.domain.report.ReportRepository;
import com.veryal.domain.report.ReportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户举报。
 */
@Service
@RequiredArgsConstructor
public class ReportApplicationService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public ReportView submit(long reporterId, ReportTargetType targetType, String targetId,
                             String reason, String detail) {
        if (targetType == null || targetId == null || targetId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid report target");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "report reason must be 1-64 characters");
        }
        String normalizedDetail = detail == null ? null : detail.trim();
        if (normalizedDetail != null && normalizedDetail.length() > 512) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "report detail is too long");
        }
        validateTarget(targetType, targetId, reporterId);
        Report report = Report.create(idGenerator.nextId(), reporterId, targetType,
                targetId.trim(), normalizedReason, normalizedDetail, timeProvider.now());
        reportRepository.save(report);
        return ReportView.from(report);
    }

    @Transactional(readOnly = true)
    public List<ReportView> mine(long reporterId, int limit) {
        return reportRepository.listByReporter(reporterId, limit).stream()
                .map(ReportView::from)
                .toList();
    }

    private void validateTarget(ReportTargetType type, String targetId, long reporterId) {
        switch (type) {
            case USER -> {
                long userId;
                try {
                    userId = Long.parseLong(targetId);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid user id");
                }
                if (userId == reporterId) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot report yourself");
                }
                userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            }
            case GROUP -> groupRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
            case MESSAGE -> {
                long messageId;
                try {
                    messageId = Long.parseLong(targetId);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid message id");
                }
                messageRepository.findById(messageId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
            }
        }
    }

    public record ReportView(long id, String targetType, String targetId, String reason,
                             String detail, String status, java.time.Instant createdAt) {

        static ReportView from(Report report) {
            return new ReportView(report.id(), report.targetType().name(), report.targetId(),
                    report.reason(), report.detail(), report.status().name(), report.createdAt());
        }
    }
}
