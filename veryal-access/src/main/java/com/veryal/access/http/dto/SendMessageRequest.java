package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * HTTP 发送消息请求。
 *
 * <p>TEXT 使用 {@code content}（UTF-8）；二进制类型使用 {@code contentBase64}。</p>
 */
public record SendMessageRequest(
                                 @Schema(description = "会话 ID。单聊形如 p_{用户A}_{用户B}，群聊即群 ID")
                                 @NotBlank @Size(max = 64) String conversationId,
                                 @NotBlank @Size(max = 64) String clientMessageId,
                                 @Schema(description = "类型")
                                 @NotBlank String type,
                                 @Schema(description = "消息内容。文本为原文，其他类型为附件引用")
                                 @Size(max = 262144) String content,
                                 @Size(max = 262144) String contentBase64,
                                 @Schema(description = "引用的原消息 ID；为空表示普通消息")
                                 Long replyToId
) {

    public SendMessageRequest(String conversationId, String clientMessageId, String type,
                              String content, String contentBase64) {
        this(conversationId, clientMessageId, type, content, contentBase64, null);
    }
}
