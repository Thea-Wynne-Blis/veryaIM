package com.veryal.access.http.dto;

import com.veryal.domain.audit.AuditLog;

import java.time.Instant;

/**
 * 审计记录响应。
 */
public record AuditLogResponse(String action, String targetType, String targetId, String detail,
                               Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.action(), log.targetType(), log.targetId(), log.detail(),
                log.createdAt());
    }
}
