package com.veryal.domain.platform;

import java.time.Instant;

/**
 * 平台管理员账号，独立于租户用户。
 */
public record PlatformAdmin(long id, String username, String displayName, String passwordHash,
                            PlatformAdminRole role, PlatformAdminStatus status,
                            int failedAttempts, Instant lockedUntil, String mfaSecret,
                            boolean mfaEnabled, Instant createdAt, Instant lastLoginAt) {

    public PlatformAdmin {
        if (id <= 0) {
            throw new IllegalArgumentException("admin id must be positive");
        }
        if (username == null || !username.matches("[A-Za-z0-9_]{3,32}")) {
            throw new IllegalArgumentException("invalid admin username");
        }
        if (role == null || status == null || createdAt == null) {
            throw new IllegalArgumentException("role/status/createdAt must not be null");
        }
    }

    public boolean active() {
        return status == PlatformAdminStatus.ACTIVE;
    }
}
