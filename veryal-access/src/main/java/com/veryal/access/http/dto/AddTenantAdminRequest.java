package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 任命租户管理员。
 */
public record AddTenantAdminRequest(@NotNull Long userId) {
}
