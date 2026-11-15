package com.veryal.access.http.dto;

import com.veryal.domain.tenant.Tenant;

import java.time.Instant;

/**
 * 租户响应。
 */
public record TenantResponse(long tenantId, String code, String name, String status,
                             int maxUsers, int maxGroups, long maxStorageBytes,
                             Instant createdAt) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.id(), tenant.code(), tenant.name(),
                tenant.status().name(), tenant.maxUsers(), tenant.maxGroups(),
                tenant.maxStorageBytes(), tenant.createdAt());
    }
}
