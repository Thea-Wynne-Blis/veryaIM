package com.veryal.access.http.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 平台侧创建租户。
 */
public record CreateTenantRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{2,32}$") String code,
        @NotBlank @Size(max = 128) String name,
        @Min(0) int maxUsers,
        @Min(0) int maxGroups,
        @Min(0) long maxStorageBytes) {
}
