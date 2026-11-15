package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 平台管理员重置密码。
 */
public record ResetPlatformPasswordRequest(@NotBlank @Size(min = 8, max = 64) String password) {
}
