package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 平台 Refresh Token 请求。
 */
public record PlatformRefreshRequest(
                                 @Schema(description = "刷新令牌")
                                 @NotBlank String refreshToken
) {
}
