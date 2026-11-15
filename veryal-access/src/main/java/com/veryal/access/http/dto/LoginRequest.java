package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。deviceId 由客户端生成并持久化，用于多端识别。
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank @Size(max = 64) String deviceId,
        @Size(max = 32) String platform,
        @Size(max = 128) String deviceName,
        @Size(max = 32) String tenantCode) {
}
