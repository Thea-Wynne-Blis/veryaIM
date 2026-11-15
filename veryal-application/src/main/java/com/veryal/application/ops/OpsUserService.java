package com.veryal.application.ops;

import com.veryal.application.platform.PlatformAuditRecorder;
import com.veryal.common.core.TenantIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.identity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 运营后台用户管理。
 */
@Service
@RequiredArgsConstructor
public class OpsUserService {

    private final UserRepository userRepository;
    private final PlatformAuditRecorder auditRecorder;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public List<OpsUserView> search(String keyword, int limit) {
        String value = keyword == null ? "" : keyword.trim();
        return userRepository.searchAll(TenantIds.DEFAULT_TENANT_ID, value,
                        Math.max(1, Math.min(limit, 100))).stream()
                .map(OpsUserView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OpsUserView get(long userId) {
        return OpsUserView.from(require(userId));
    }

    @Transactional
    public OpsUserView setStatus(long adminId, long userId, UserStatus status, String reason) {
        User user = require(userId);
        User updated = user.withStatus(status, timeProvider.now());
        userRepository.update(updated);
        auditRecorder.record(adminId, "OPS_USER_STATUS", "user", Long.toString(userId),
                "status=" + status + ",reason=" + (reason == null ? "" : reason));
        return OpsUserView.from(updated);
    }

    private User require(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public record OpsUserView(long userId, String username, String nickname, String avatar,
                              String status, Instant createdAt) {

        static OpsUserView from(User user) {
            return new OpsUserView(user.id(), user.username(), user.nickname(), user.avatar(),
                    user.status().name(), user.createdAt());
        }
    }
}
