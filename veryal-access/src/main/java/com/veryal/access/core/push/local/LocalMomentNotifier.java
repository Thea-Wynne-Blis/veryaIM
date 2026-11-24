package com.veryal.access.core.push.local;

import com.veryal.domain.moment.MomentEvent;
import com.veryal.domain.moment.MomentNotifier;
import com.veryal.domain.moment.MomentPushPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQ 关闭时的朋友圈事件本机直推。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class LocalMomentNotifier implements MomentNotifier {

    private final MomentPushPort momentPushPort;

    @Override
    public void notify(MomentEvent event) {
        momentPushPort.push(event);
    }
}
