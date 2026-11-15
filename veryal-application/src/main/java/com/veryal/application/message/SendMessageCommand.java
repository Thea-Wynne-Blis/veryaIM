package com.veryal.application.message;

import com.veryal.domain.message.MessageType;

/**
 * 发送消息命令。
 *
 * @param senderDeviceId 发送设备 ID，用于实时推送时跳过发送端自身连接
 */
public record SendMessageCommand(long senderId, String senderDeviceId,
                                 String conversationId, String clientMessageId,
                                 MessageType messageType, byte[] content) {
}
