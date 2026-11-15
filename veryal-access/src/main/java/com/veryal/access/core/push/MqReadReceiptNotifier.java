package com.veryal.access.core.push;

import com.veryal.domain.message.event.ReadReceiptEvent;
import com.veryal.domain.message.event.ReadReceiptNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 开启时由广播消费者推送，本机入口为空实现。
 */
@Component
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MqReadReceiptNotifier implements ReadReceiptNotifier {

    @Override
    public void notify(ReadReceiptEvent event) {
        // RabbitMQ 消费者统一广播
    }
}
