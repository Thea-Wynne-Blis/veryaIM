package com.veryal.access.core.push.mq;

import com.veryal.domain.message.event.MessageRecallNotifier;
import com.veryal.domain.message.event.MessageRecalledEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 开启时由广播消费者推送，本机入口为空实现。
 */
@Component
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MqMessageRecallNotifier implements MessageRecallNotifier {

    @Override
    public void notify(MessageRecalledEvent event) {
        // RabbitMQ 消费者统一广播
    }
}
