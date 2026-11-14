package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.delivery.GatewayPushCommand;
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
import java.util.List;
import java.util.Map;

/**
 * 消息投递消费者：消费全局单投递队列，按 Redis 连接路由拆分到各 Gateway 推送队列。
 *
 * <p>手动 ACK；失败时按有限次数转入 TTL 重试队列，超过上限进入死信队列。
 * 只有消费到事件的实例负责路由，避免每实例重复消费。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MessageDeliveryListener {

    private final GatewayRouter gatewayRouter;
    private final DeliveryDeduplicator deduplicator;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;

    @RabbitListener(queues = RabbitConstants.DELIVERY_QUEUE, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = RabbitConstants.HEADER_RETRY_COUNT, required = false) Integer retryCount)
            throws IOException {
        MessageCreatedEvent event = null;
        try {
            event = JsonUtils.fromJson(message.getBody(), MessageCreatedEvent.class);
            if (deduplicator.firstSeen(event.eventId())) {
                route(event);
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

    private void route(MessageCreatedEvent event) {
        Map<String, List<String>> routes = gatewayRouter.route(event);
        if (routes.isEmpty()) {
            log.debug("no online route for message: messageId={}", event.messageId());
            return;
        }
        for (Map.Entry<String, List<String>> entry : routes.entrySet()) {
            GatewayPushCommand command = new GatewayPushCommand(event.eventId(), entry.getKey(),
                    entry.getValue(), event);
            rabbitTemplate.convertAndSend(RabbitConstants.GATEWAY_EXCHANGE,
                    RabbitConstants.GATEWAY_PUSH_ROUTING_PREFIX + entry.getKey(),
                    JsonUtils.toJsonBytes(command), message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setMessageId(event.eventId());
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
        }
    }
}
