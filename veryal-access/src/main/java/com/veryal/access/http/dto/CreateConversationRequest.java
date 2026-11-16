package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 创建单聊会话请求。
 */
public record CreateConversationRequest(
                                 @Schema(description = "对方用户 ID")
                                 @NotNull Long peerUserId
) {
}
