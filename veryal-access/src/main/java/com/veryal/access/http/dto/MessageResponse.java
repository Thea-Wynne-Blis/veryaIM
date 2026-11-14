package com.veryal.access.http.dto;

import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 消息响应。TEXT 消息 content 为 UTF-8 文本，其它类型为 Base64。
 */
public record MessageResponse(long messageId, String conversationId, long senderId, long sequence,
                              String type, String content, String contentEncoding, Instant createdAt) {

    public static MessageResponse from(Message message) {
        boolean text = message.type() == MessageType.TEXT;
        String content = text
                ? new String(message.content(), StandardCharsets.UTF_8)
                : Base64.getEncoder().encodeToString(message.content());
        return new MessageResponse(message.id(), message.conversationId(), message.senderId(), message.seq(),
                message.type().name(), content, text ? "utf8" : "base64", message.createdAt());
    }
}
