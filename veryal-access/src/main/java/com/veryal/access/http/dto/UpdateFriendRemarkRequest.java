package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 修改好友备注。
 */
public record UpdateFriendRemarkRequest(
                                 @Schema(description = "好友备注名")
                                 @Size(max = 64) String remark
) {
}
