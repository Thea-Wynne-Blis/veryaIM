package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 发送好友申请。
 */
public record CreateFriendRequest(
                                 @Schema(description = "被申请人用户 ID")
                                 @NotNull Long toUserId,
                                 @Schema(description = "提示信息，可直接展示")
                                 @Size(max = 255) String message
) {
}
