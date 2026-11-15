package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 平台 Refresh Token 请求。
 */
public record PlatformRefreshRequest(@NotBlank String refreshToken) {
}
