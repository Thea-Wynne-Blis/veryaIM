package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 创建平台管理员。
 */
public record CreatePlatformAdminRequest(
                                 @Schema(description = "登录用户名")
                                 @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$") String username,
                                 @Schema(description = "密码（明文传输需 HTTPS）")
                                 @NotBlank @Size(min = 8, max = 64) String password,
                                 @Size(max = 64) String displayName,
                                 @Schema(description = "角色")
                                 @NotBlank String role
) {
}
