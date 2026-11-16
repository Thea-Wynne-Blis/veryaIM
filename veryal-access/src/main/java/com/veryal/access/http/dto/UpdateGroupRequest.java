package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 修改群资料请求。
 */
public record UpdateGroupRequest(
                                 @Schema(description = "名称")
                                 @NotBlank @Size(max = 128) String name
) {
}
