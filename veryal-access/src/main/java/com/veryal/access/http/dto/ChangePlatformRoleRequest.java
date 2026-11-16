package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 平台管理员角色调整。
 */
public record ChangePlatformRoleRequest(
                                 @Schema(description = "角色")
                                 @NotBlank String role
) {
}
