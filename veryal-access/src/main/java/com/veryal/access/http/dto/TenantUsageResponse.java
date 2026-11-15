package com.veryal.access.http.dto;

import com.veryal.application.tenant.TenantAdminService;

/**
 * 租户用量/账单。
 */
public record TenantUsageResponse(long tenantId, String tenantCode, long users, long groups,
                                  long messages, long storageBytes, long admins,
                                  long estimatedMonthlyCents) {

    public static TenantUsageResponse from(TenantAdminService.TenantUsageView view) {
        return new TenantUsageResponse(view.tenantId(), view.tenantCode(), view.users(),
                view.groups(), view.messages(), view.storageBytes(), view.admins(),
                view.estimatedMonthlyCents());
    }
}
