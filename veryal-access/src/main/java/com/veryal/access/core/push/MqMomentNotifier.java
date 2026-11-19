package com.veryal.access.core.push;

import com.veryal.domain.moment.MomentEvent;
import com.veryal.domain.moment.MomentNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 开启时由广播消费者推送，本机入口为空实现。
 */
@Component
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MqMomentNotifier implements MomentNotifier {

    @Override
    public void notify(MomentEvent event) {
        // RabbitMQ 消费者统一广播
    }
}
