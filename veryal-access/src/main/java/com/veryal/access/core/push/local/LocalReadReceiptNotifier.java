package com.veryal.access.core.push.local;

import com.veryal.domain.message.event.ReadReceiptEvent;
import com.veryal.domain.message.event.ReadReceiptNotifier;
import com.veryal.domain.message.event.ReadReceiptPushPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 关闭时的已读回执本机直推。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class LocalReadReceiptNotifier implements ReadReceiptNotifier {

    private final ReadReceiptPushPort readReceiptPushPort;

    @Override
    public void notify(ReadReceiptEvent event) {
        readReceiptPushPort.push(event);
    }
}
