package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.message.event.MessageEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消息投递消费者。每个实例消费自己的投递队列，只推送本节点在线连接。
 *
 * <p>手动 ACK；失败时按有限次数转入实例级 TTL 重试队列，超过上限进入共享死信队列。
 * 消费端通过 {@link RealtimeDeliveryPort} 推送，不直接依赖 access 层。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MessageDeliveryListener {

    private final RealtimeDeliveryPort realtimeDeliveryPort;
    private final DeliveryDeduplicator deduplicator;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitInstance rabbitInstance;
    private final OutboxProperties properties;

    @RabbitListener(queues = "#{@messageDeliveryQueue.name}", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = RabbitConstants.HEADER_RETRY_COUNT, required = false) Integer retryCount)
            throws IOException {
        MessageCreatedEvent event = null;
        try {
            event = JsonUtils.fromJson(message.getBody(), MessageCreatedEvent.class);
            if (deduplicator.firstSeen(event.eventId())) {
                realtimeDeliveryPort.deliver(event);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            int attempt = retryCount == null ? 0 : retryCount;
            String retryRoutingKey = event == null ? MessageEvents.PRIVATE_CREATED : event.eventType();
            log.warn("message delivery failed: attempt={}, reason={}", attempt, e.getMessage());
            try {
                if (attempt >= properties.getMaxRetries()) {
                    rabbitTemplate.send(RabbitConstants.DEAD_LETTER_EXCHANGE,
                            RabbitConstants.DEAD_ROUTING_KEY, message);
                    log.error("message delivery moved to dead letter queue after {} attempts", attempt);
                } else {
                    rabbitTemplate.convertAndSend(RabbitConstants.RETRY_EXCHANGE,
                            retryRoutingKey, message.getBody(), retryMessage -> {
                                MessageProperties props = retryMessage.getMessageProperties();
                                props.setHeader(RabbitConstants.HEADER_RETRY_COUNT, attempt + 1);
                                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                                return retryMessage;
                            });
                }
                channel.basicAck(deliveryTag, false);
            } catch (Exception retryFailure) {
                log.error("failed to schedule retry, requeue original message", retryFailure);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
