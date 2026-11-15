package com.veryal.infrastructure.mq;

import com.veryal.common.core.RandomIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 当前实例的 MQ 命名与路由键。
 *
 * <p>投递队列为全局单队列；每个 Gateway 实例声明自己的推送队列，
 * 绑定 {@code gateway.push.{gatewayId}}，只接收需要本节点推送的连接。</p>
 */
@Component
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class RabbitInstance {

    private final String gatewayId;
    private final String instanceId;

    public RabbitInstance(@Value("${veryal.gateway.id:gateway-local}") String gatewayId) {
        this.gatewayId = gatewayId;
        this.instanceId = gatewayId + "-" + RandomIds.newId().substring(0, 8);
    }

    public String gatewayId() {
        return gatewayId;
    }

    public String instanceId() {
        return instanceId;
    }

    public String gatewayPushQueueName() {
        return RabbitConstants.GATEWAY_PUSH_QUEUE + "." + instanceId;
    }

    public String gatewayPushRoutingKey() {
        return RabbitConstants.GATEWAY_PUSH_ROUTING_PREFIX + gatewayId;
    }

    public String readReceiptQueueName() {
        return RabbitConstants.READ_RECEIPT_QUEUE + "." + instanceId;
    }

    public String messageRecallQueueName() {
        return RabbitConstants.MESSAGE_RECALL_QUEUE + "." + instanceId;
    }

    public String friendEventQueueName() {
        return RabbitConstants.FRIEND_EVENT_QUEUE + "." + instanceId;
    }
}
