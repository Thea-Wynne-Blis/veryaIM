package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建平台管理员。
 */
public record CreatePlatformAdminRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$") String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @Size(max = 64) String displayName,
        @NotBlank String role) {
}
