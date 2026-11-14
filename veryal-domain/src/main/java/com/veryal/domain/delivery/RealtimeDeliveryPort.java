package com.veryal.domain.delivery;

import com.veryal.domain.message.event.MessageCreatedEvent;

/**
 * 实时投递端口。
 *
 * <p>由接入层实现（推送到本节点在线连接）；MQ 消费者只依赖该端口，
 * 避免基础设施层反向依赖 access。</p>
 */
public interface RealtimeDeliveryPort {

    void deliver(MessageCreatedEvent event);
}
