package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.message.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.message.MessageType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Base64;

/**
 * 消息响应。TEXT 消息 content 为 UTF-8 文本，其它类型为 Base64；已撤回消息不回传原文。
 */
public record MessageResponse(
                                 @Schema(description = "消息 ID")
                                 long messageId,
                                 @Schema(description = "会话 ID。单聊形如 p_{用户A}_{用户B}，群聊即群 ID")
                                 String conversationId,
                                 @Schema(description = "发送者 ID")
                                 long senderId,
                                 long sequence,
                                 @Schema(description = "类型")
                                 String type,
                                 @Schema(description = "消息内容。文本为原文，其他类型为附件引用")
                                 String content,
                                 String contentEncoding,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt,
                                 boolean recalled,
                                 @Schema(description = "撤回时间，非空表示已撤回")
                                 Instant recalledAt,
                                 @Schema(description = "引用的原消息 ID，未引用时为空")
                                 Long replyToId,
                                 @Schema(description = "被引用消息的发送者 ID")
                                 Long replySenderId,
                                 @Schema(description = "被引用消息的文本快照")
                                 String replyPreview
) {

    public static MessageResponse from(Message message) {
        boolean text = message.type() == MessageType.TEXT;
        String content = message.recalled()
                ? ""
                : text ? new String(message.content(), StandardCharsets.UTF_8)
                : Base64.getEncoder().encodeToString(message.content());
        return new MessageResponse(message.id(), message.conversationId(), message.senderId(), message.seq(),
                message.type().name(), content, text ? "utf8" : "base64", message.createdAt(),
                message.recalled(), message.recalledAt(),
                message.replyToId(), message.replySenderId(), message.replyPreview());
    }
}
