package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 删除租户确认。
 */
public record DeleteTenantRequest(@NotBlank String confirmCode) {
}
