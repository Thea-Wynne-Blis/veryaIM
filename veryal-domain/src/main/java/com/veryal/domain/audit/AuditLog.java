package com.veryal.domain.audit;

import java.time.Instant;

/**
 * 审计记录。
 */
public record AuditLog(long id, long operatorId, String action, String targetType,
                       String targetId, String detail, Instant createdAt) {

    public AuditLog {
        if (id <= 0 || operatorId <= 0) {
            throw new IllegalArgumentException("audit ids must be positive");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("targetType must not be blank");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (detail != null && detail.length() > 512) {
            detail = detail.substring(0, 512);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
