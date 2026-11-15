package com.veryal.application.message.strategy;

import com.veryal.domain.message.MessageType;

/**
 * 消息内容校验策略上下文。
 */
public record MessageContentContext(long senderId, String conversationId,
                                    MessageType type, byte[] content) {

    public MessageContentContext {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (type == null || content == null) {
            throw new IllegalArgumentException("type/content must not be null");
        }
    }
}
