package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 收藏一张网络表情包请求。
 *
 * @param url            表情图片地址，必须来自允许的表情站点
 * @param conversationId 目标会话；服务端会校验当前用户是会话成员
 */
public record CollectStickerRequest(
        @Schema(description = "表情图片地址")
        @NotBlank @Size(max = 1024) String url,
        @Schema(description = "目标会话 ID")
        @NotBlank @Size(max = 64) String conversationId
) {
}
