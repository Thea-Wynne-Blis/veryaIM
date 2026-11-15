package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.friend.FriendPushPort;
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
 * 好友事件广播消费者：每个 Gateway 实例向本节点上的申请双方推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class FriendEventListener {

    private final FriendPushPort friendPushPort;

    @RabbitListener(queues = "#{@friendEventQueue.name}", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            FriendEvent event = JsonUtils.fromJson(message.getBody(), FriendEvent.class);
            friendPushPort.push(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("friend event push failed", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
