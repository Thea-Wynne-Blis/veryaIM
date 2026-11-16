package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 设置群成员角色请求，role 仅支持 ADMIN / MEMBER。
 */
public record GroupRoleRequest(
                                 @Schema(description = "角色")
                                 @NotBlank String role
) {
}
