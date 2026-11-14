package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.delivery.GatewayPushCommand;
import com.veryal.domain.delivery.GatewayPushPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Gateway 推送消费者：只消费绑定到当前 gatewayId 的推送命令，并向本节点连接写 Channel。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class GatewayPushListener {

    private final GatewayPushPort gatewayPushPort;

    @RabbitListener(queues = "#{@gatewayPushQueue.name}", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            GatewayPushCommand command = JsonUtils.fromJson(message.getBody(), GatewayPushCommand.class);
            gatewayPushPort.push(command.message(), command.connectionIds());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("gateway push failed, reject to dead letter queue", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
