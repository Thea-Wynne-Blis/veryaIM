package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 平台管理员重置密码。
 */
public record ResetPlatformPasswordRequest(
                                 @Schema(description = "密码（明文传输需 HTTPS）")
                                 @NotBlank @Size(min = 8, max = 64) String password
) {
}
