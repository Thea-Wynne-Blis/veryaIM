package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建群请求。创建者自动成为群主。
 */
public record CreateGroupRequest(
        @NotBlank @Size(max = 128) String name,
        List<Long> memberIds) {
}
