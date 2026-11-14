package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 设置群成员角色请求，role 仅支持 ADMIN / MEMBER。
 */
public record GroupRoleRequest(@NotBlank String role) {
}
