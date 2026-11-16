package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.platform.PlatformAuditLog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 平台审计记录响应。
 */
public record PlatformAuditResponse(
                                 @Schema(description = "管理员 ID")
                                 long adminId,
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

    public static PlatformAuditResponse from(PlatformAuditLog log) {
        return new PlatformAuditResponse(log.adminId(), log.action(), log.targetType(),
                log.targetId(), log.detail(), log.createdAt());
    }
}
