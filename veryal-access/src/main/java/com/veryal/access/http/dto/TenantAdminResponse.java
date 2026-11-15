package com.veryal.access.http.dto;

import com.veryal.application.tenant.TenantAdminService;

/**
 * 租户管理员响应。
 */
public record TenantAdminResponse(long userId, String nickname, String avatar) {

    public static TenantAdminResponse from(TenantAdminService.TenantAdminView view) {
        return new TenantAdminResponse(view.userId(), view.nickname(), view.avatar());
    }
}
