package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 平台管理员角色调整。
 */
public record ChangePlatformRoleRequest(@NotBlank String role) {
}
