package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP 发送消息请求。
 *
 * <p>TEXT 使用 {@code content}（UTF-8）；二进制类型使用 {@code contentBase64}。</p>
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 64) String conversationId,
        @NotBlank @Size(max = 64) String clientMessageId,
        @NotBlank String type,
        @Size(max = 262144) String content,
        @Size(max = 262144) String contentBase64) {
}
