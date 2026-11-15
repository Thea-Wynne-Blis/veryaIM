package com.veryal.domain.tenant;

import java.time.Instant;

/**
 * 租户。0 配额表示不限制。
 */
public record Tenant(long id, String code, String name, TenantStatus status, int maxUsers,
                     int maxGroups, long maxStorageBytes, Instant createdAt, Instant updatedAt) {

    public Tenant {
        if (id <= 0) {
            throw new IllegalArgumentException("tenant id must be positive");
        }
        if (code == null || !code.matches("[a-z0-9-]{2,32}")) {
            throw new IllegalArgumentException("tenant code must match [a-z0-9-]{2,32}");
        }
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("invalid tenant name");
        }
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException("status/createdAt must not be null");
        }
    }

    public boolean active() {
        return status == TenantStatus.ACTIVE;
    }
}
