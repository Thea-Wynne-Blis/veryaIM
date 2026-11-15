package com.veryal.access.core.push;

import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.friend.FriendNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 开启时由广播消费者推送，本机入口为空实现。
 */
@Component
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class MqFriendNotifier implements FriendNotifier {

    @Override
    public void notify(FriendEvent event) {
        // RabbitMQ 消费者统一广播
    }
}
