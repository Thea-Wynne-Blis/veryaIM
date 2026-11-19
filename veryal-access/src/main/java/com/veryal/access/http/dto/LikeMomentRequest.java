package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 点赞 / 取消点赞。
 */
public record LikeMomentRequest(
        @Schema(description = "true 点赞，false 取消") boolean liked
) {
}
