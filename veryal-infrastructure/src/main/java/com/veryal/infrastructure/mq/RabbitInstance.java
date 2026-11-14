package com.veryal.infrastructure.mq;

import com.veryal.common.core.RandomIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 当前实例的 MQ 拓扑命名。
 *
 * <p>实时投递采用“每实例独立队列”的广播模型：每个 Gateway 实例都会收到全部消息事件，
 * 只向本节点在线连接推送。这样多实例部署时无需 Redis 路由也能保证命中接收者所在节点。
 * 后续可用 Redis 路由 + 单队列减少重复消费。</p>
 */
@Component
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class RabbitInstance {

    private final String instanceId;

    public RabbitInstance(@Value("${veryal.gateway.id:gateway-local}") String gatewayId) {
        this.instanceId = gatewayId + "-" + RandomIds.newId().substring(0, 8);
    }

    public String instanceId() {
        return instanceId;
    }

    public String deliveryQueueName() {
        return RabbitConstants.DELIVERY_QUEUE + "." + instanceId;
    }

    public String retryQueueName() {
        return RabbitConstants.RETRY_QUEUE + "." + instanceId;
    }

    public String retryRoutingKey() {
        return RabbitConstants.RETRY_ROUTING_KEY + "." + instanceId;
    }
}
