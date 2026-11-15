package com.veryal.access.core.push;

import com.veryal.domain.message.event.MessageRecallNotifier;
import com.veryal.domain.message.event.MessageRecallPushPort;
import com.veryal.domain.message.event.MessageRecalledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 关闭时的撤回通知本机直推。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class LocalMessageRecallNotifier implements MessageRecallNotifier {

    private final MessageRecallPushPort messageRecallPushPort;

    @Override
    public void notify(MessageRecalledEvent event) {
        messageRecallPushPort.push(event);
    }
}
