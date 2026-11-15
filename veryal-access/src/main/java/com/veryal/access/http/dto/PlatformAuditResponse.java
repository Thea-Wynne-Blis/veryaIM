package com.veryal.access.http.dto;

import com.veryal.domain.platform.PlatformAuditLog;

import java.time.Instant;

/**
 * 平台审计记录响应。
 */
public record PlatformAuditResponse(long adminId, String action, String targetType,
                                    String targetId, String detail, Instant createdAt) {

    public static PlatformAuditResponse from(PlatformAuditLog log) {
        return new PlatformAuditResponse(log.adminId(), log.action(), log.targetType(),
                log.targetId(), log.detail(), log.createdAt());
    }
}
