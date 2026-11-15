package com.veryal.domain.tenant;

import java.time.Instant;

/**
 * 租户管理员：租户内某个用户的授权。
 */
public record TenantAdmin(long tenantId, long userId, Instant createdAt) {

    public TenantAdmin {
        if (tenantId <= 0 || userId <= 0 || createdAt == null) {
            throw new IllegalArgumentException("invalid tenant admin");
        }
    }
}
