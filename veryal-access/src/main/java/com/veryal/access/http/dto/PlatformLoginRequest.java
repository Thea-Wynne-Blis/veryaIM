package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 平台管理员登录。
 */
public record PlatformLoginRequest(@NotBlank @Size(max = 32) String username,
                                   @NotBlank @Size(max = 128) String password) {
}
