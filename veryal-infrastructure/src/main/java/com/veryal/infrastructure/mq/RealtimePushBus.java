package com.veryal.infrastructure.mq;

import com.veryal.common.json.JsonUtils;
import com.veryal.domain.delivery.RealtimeBusPort;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 跨实例推送总线实现（fanout 广播）。
 *
 * <p>发送节点：本地直推 → 广播到交换机；每个实例（含发送节点自己）都会收到，
 * 由 {@link RealtimeDeliveryPort} 实现按 eventId 去重，因此发送节点不会重复推送，
 * 其他节点则把消息推给自己持有的连接与 SSE 订阅。</p>
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

    @RabbitListener(queues = "#{realtimePushQueue.name}")
    public void onPush(byte[] payload) {
        try {
            MessageCreatedEvent event = JsonUtils.fromJson(payload, MessageCreatedEvent.class);
            realtimeDeliveryPort.deliver(event);
        } catch (RuntimeException e) {
            log.warn("realtime bus consume failed: reason={}", e.getMessage());
        }
    }
}
