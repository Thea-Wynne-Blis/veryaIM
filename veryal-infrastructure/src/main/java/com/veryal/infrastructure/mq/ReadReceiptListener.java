package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.message.event.ReadReceiptEvent;
import com.veryal.domain.message.event.ReadReceiptPushPort;
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
 * 已读回执广播消费者：每个 Gateway 实例都收到事件，只向本节点成员连接推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class ReadReceiptListener {

    private final ReadReceiptPushPort readReceiptPushPort;

    @RabbitListener(queues = "#{@readReceiptQueue.name}", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            ReadReceiptEvent event = JsonUtils.fromJson(message.getBody(), ReadReceiptEvent.class);
            readReceiptPushPort.push(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("read receipt push failed", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
