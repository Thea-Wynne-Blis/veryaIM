package com.veryal.application.message;

import com.veryal.domain.message.MessageType;

/**
 * 发送消息命令。
 */
public record SendMessageCommand(long tenantId, long senderId, String conversationId,
                                 String clientMessageId, MessageType messageType, byte[] content) {
}
