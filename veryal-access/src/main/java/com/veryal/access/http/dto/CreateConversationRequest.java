package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 创建单聊会话请求。
 */
public record CreateConversationRequest(@NotNull Long peerUserId) {
}
