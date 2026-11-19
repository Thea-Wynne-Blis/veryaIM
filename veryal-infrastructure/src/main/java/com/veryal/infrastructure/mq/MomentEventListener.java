package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.moment.MomentEvent;
import com.veryal.domain.moment.MomentPushPort;
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
 * 朋友圈互动事件广播消费者：每个 Gateway 实例向本节点的接收人推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MomentEventListener {

    private final MomentPushPort momentPushPort;

    @RabbitListener(queues = "#{@momentEventQueue.name}", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            MomentEvent event = JsonUtils.fromJson(message.getBody(), MomentEvent.class);
            momentPushPort.push(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("moment event push failed", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
