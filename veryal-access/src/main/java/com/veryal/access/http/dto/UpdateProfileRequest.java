package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 修改资料请求。
 */
public record UpdateProfileRequest(
                                 @Schema(description = "昵称")
                                 @Size(max = 64) String nickname,
                                 @Schema(description = "头像地址")
                                 @Size(max = 512) String avatar
) {
}
