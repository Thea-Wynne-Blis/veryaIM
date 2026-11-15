package com.veryal.domain.platform;

import java.time.Instant;

/**
 * 平台管理员操作审计。
 */
public record PlatformAuditLog(long id, long adminId, String action, String targetType,
                               String targetId, String detail, Instant createdAt) {

    public PlatformAuditLog {
        if (id <= 0 || adminId <= 0) {
            throw new IllegalArgumentException("platform audit ids must be positive");
        }
        if (action == null || action.isBlank() || targetType == null || targetType.isBlank()
                || targetId == null || targetId.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("invalid platform audit fields");
        }
        if (detail != null && detail.length() > 512) {
            detail = detail.substring(0, 512);
        }
    }
}
