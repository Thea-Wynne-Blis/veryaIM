package com.veryal.domain.delivery;

import com.veryal.domain.message.event.MessageCreatedEvent;

/**
 * 跨实例实时推送总线。
 *
 * <p>发送节点先本地直推，再通过该端口广播；其他实例收到后只推自己的连接。
 * MQ 关闭时不注册实现，单机模式无需该能力。</p>
 */
public interface RealtimeBusPort {

    void publish(MessageCreatedEvent event);
}
