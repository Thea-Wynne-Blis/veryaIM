package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 平台管理员登录；MFA 开启后需要 6 位动态码。
 */
public record PlatformLoginRequest(
                                 @Schema(description = "登录用户名")
                                 @NotBlank @Size(max = 32) String username,
                                 @Schema(description = "密码（明文传输需 HTTPS）")
                                 @NotBlank @Size(max = 128) String password,
                                 @Pattern(regexp = "^\\d{6}$") String mfaCode
) {
}
