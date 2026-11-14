package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改群资料请求。
 */
public record UpdateGroupRequest(@NotBlank @Size(max = 128) String name) {
}
