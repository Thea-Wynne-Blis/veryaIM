package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 创建群请求。创建者自动成为群主。
 */
public record CreateGroupRequest(
                                 @Schema(description = "名称")
                                 @NotBlank @Size(max = 128) String name,
                                 @Schema(description = "成员用户 ID 列表")
                                 List<Long> memberIds
) {
}
