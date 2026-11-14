package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "username must be 3-32 letters, digits or underscore")
        String username,

        @NotBlank
        @Size(min = 8, max = 64)
        String password,

        @Size(max = 64)
        String nickname) {
}
