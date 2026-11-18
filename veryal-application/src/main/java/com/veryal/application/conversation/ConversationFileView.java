package com.veryal.application.conversation;

import java.time.Instant;

/**
 * 会话文件列表项：来自一条附件消息，元信息以文件表为准。
 *
 * @param messageType IMAGE / FILE / AUDIO / VIDEO
 * @param name        原始文件名
 */
public record ConversationFileView(long fileId, long messageId, String messageType,
                                   String name, long sizeBytes, String contentType,
                                   long senderId, String senderName, Instant createdAt) {
}
