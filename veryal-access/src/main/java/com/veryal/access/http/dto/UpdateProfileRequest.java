package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料。
 */
public record UpdateProfileRequest(
                                 @Schema(description = "昵称")
                                 @Size(max = 64) String nickname,
                                 @Schema(description = "头像地址")
                                 @Size(max = 512) String avatar,
                                 @Schema(description = "所在地区，最多 64 字；传空串表示清空")
                                 @Size(max = 64) String region,
                                 @Schema(description = "个性签名，最多 255 字；传空串表示清空")
                                 @Size(max = 255) String signature
) {
}
