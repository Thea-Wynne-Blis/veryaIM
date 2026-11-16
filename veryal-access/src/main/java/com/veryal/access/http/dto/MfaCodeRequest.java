package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * MFA 动态码。
 */
public record MfaCodeRequest(
                                 @Schema(description = "业务码，0 表示成功")
                                 @NotBlank @Pattern(regexp = "^\\d{6}$") String code
) {
}
