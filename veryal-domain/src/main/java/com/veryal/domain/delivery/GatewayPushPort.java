package com.veryal.domain.delivery;

import com.veryal.domain.message.event.MessageCreatedEvent;

import java.util.List;

/**
 * 本节点连接推送端口，由 access 层实现。
 */
public interface GatewayPushPort {

    void push(MessageCreatedEvent message, List<String> connectionIds);
}
