package com.veryal.application.message.strategy;

import com.veryal.domain.message.MessageType;

/**
 * 消息类型策略：每种消息类型独立校验内容格式与关联资源。
 */
public interface MessageTypeStrategy {

    MessageType type();

    void validate(MessageContentContext context);
}
