package com.veryal.application.message.strategy;

import com.veryal.domain.message.MessageType;

/**
 * 消息内容校验策略上下文。
 */
public record MessageContentContext(long tenantId, long senderId, MessageType type, byte[] content) {

    public MessageContentContext {
        if (tenantId <= 0 || senderId <= 0) {
            throw new IllegalArgumentException("tenantId/senderId must be positive");
        }
        if (type == null || content == null) {
            throw new IllegalArgumentException("type/content must not be null");
        }
    }
}
