package com.veryal.infrastructure.mq;

import com.rabbitmq.client.Channel;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.delivery.RealtimeBusPort;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 跨实例推送总线实现（fanout 广播）。
 *
 * <p>发送节点：本地直推 → 广播到交换机；每个实例（含发送节点自己）都会收到，
 * 由 {@link com.veryal.access.core.push.ConnectionPushDedup} 按
 * {@code eventId + connectionId} 去重，因此发送节点不会重复推送，
 * 其他节点则把消息推给自己持有的连接。</p>
 *
 * <p>广播失败只记日志，不阻塞发送；漏推由客户端重连后的增量同步补齐。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class RealtimePushBus implements RealtimeBusPort {

    private final RabbitTemplate rabbitTemplate;
    private final RealtimeDeliveryPort realtimeDeliveryPort;

    @Override
    public void publish(MessageCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RealtimeBusConfig.PUSH_EXCHANGE, "",
                    JsonUtils.toJsonBytes(event), message -> {
                        MessageProperties properties = message.getMessageProperties();
                        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        return message;
                    });
        } catch (RuntimeException e) {
            log.warn("realtime bus publish failed: eventId={}, reason={}",
                    event.eventId(), e.getMessage());
        }
    }

    @RabbitListener(queues = "#{realtimePushQueue.name}", ackMode = "MANUAL")
    public void onPush(byte[] payload, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            MessageCreatedEvent event = JsonUtils.fromJson(payload, MessageCreatedEvent.class);
            realtimeDeliveryPort.deliver(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 总线队列是每实例的临时广播队列：处理失败直接丢弃即可，
            // 漏推由客户端重连/游标增量补齐，不做重试以免毒消息阻塞后续推送。
            log.warn("realtime bus consume failed, dropped: reason={}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
