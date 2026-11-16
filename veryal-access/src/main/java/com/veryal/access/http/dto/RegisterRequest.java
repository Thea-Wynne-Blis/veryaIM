package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterRequest(
                                 @Schema(description = "登录用户名")
                                 @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "username must be 3-32 letters, digits or underscore") String username,
                                 @Schema(description = "密码（明文传输需 HTTPS）")
                                 @NotBlank @Size(min = 8, max = 64) String password,
                                 @Schema(description = "昵称")
                                 @Size(max = 64) String nickname
) {
}
