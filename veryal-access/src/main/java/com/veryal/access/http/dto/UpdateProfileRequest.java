package com.veryal.access.http.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改资料请求。
 */
public record UpdateProfileRequest(@Size(max = 64) String nickname, @Size(max = 512) String avatar) {
}
