package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * MFA 动态码。
 */
public record MfaCodeRequest(@NotBlank @Pattern(regexp = "^\\d{6}$") String code) {
}
