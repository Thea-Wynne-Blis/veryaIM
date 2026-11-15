package com.veryal.access.http.dto;

import com.veryal.domain.platform.PlatformAdmin;

import java.time.Instant;

/**
 * 平台管理员响应。
 */
public record PlatformAdminResponse(long adminId, String username, String displayName, String role,
                                    String status, Instant createdAt, Instant lastLoginAt) {

    public static PlatformAdminResponse from(PlatformAdmin admin) {
        return new PlatformAdminResponse(admin.id(), admin.username(), admin.displayName(),
                admin.role().name(), admin.status().name(), admin.createdAt(), admin.lastLoginAt());
    }
}
