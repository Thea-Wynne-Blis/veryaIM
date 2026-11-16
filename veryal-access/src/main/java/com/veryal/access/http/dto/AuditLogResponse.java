package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.audit.AuditLog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 审计记录响应。
 */
public record AuditLogResponse(
                                 @Schema(description = "执行的动作")
                                 String action,
                                 @Schema(description = "举报对象类型：USER / GROUP / MESSAGE")
                                 String targetType,
                                 @Schema(description = "举报对象 ID")
                                 String targetId,
                                 String detail,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.action(), log.targetType(), log.targetId(), log.detail(),
                log.createdAt());
    }
}
