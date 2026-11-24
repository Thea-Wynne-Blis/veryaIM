package com.veryal.access.core.push.offline;

import com.veryal.domain.push.OfflinePushMessage;
import com.veryal.domain.push.OfflinePushProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 未配置推送通道时只记录日志，不真正投递。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "veryal.push", name = "provider", havingValue = "none",
        matchIfMissing = true)
public class NoopOfflinePushProvider implements OfflinePushProvider {

    @Override
    public void push(OfflinePushMessage message) {
        log.debug("offline push (noop): userId={}, deviceId={}, platform={}, title={}, data={}",
                message.userId(), message.deviceId(), message.platform(), message.title(),
                message.data());
    }
}
