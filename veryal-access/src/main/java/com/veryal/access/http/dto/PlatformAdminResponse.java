package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.platform.PlatformAdmin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 平台管理员响应。
 */
public record PlatformAdminResponse(
                                 @Schema(description = "管理员 ID")
                                 long adminId,
                                 @Schema(description = "登录用户名")
                                 String username,
                                 String displayName,
                                 @Schema(description = "角色")
                                 String role,
                                 @Schema(description = "状态")
                                 String status,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt,
                                 Instant lastLoginAt
) {

    public static PlatformAdminResponse from(PlatformAdmin admin) {
        return new PlatformAdminResponse(admin.id(), admin.username(), admin.displayName(),
                admin.role().name(), admin.status().name(), admin.createdAt(), admin.lastLoginAt());
    }
}
