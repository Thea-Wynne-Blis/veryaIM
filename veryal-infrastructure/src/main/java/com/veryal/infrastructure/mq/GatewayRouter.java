package com.veryal.infrastructure.mq;

import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.domain.presence.PresenceRecord;
import com.veryal.domain.presence.PresenceRepository;
import com.veryal.domain.presence.PresenceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 Redis 在线状态把消息接收者路由到具体 Gateway 与连接。
 *
 * <p>路由数据来自 presence（deviceId → gatewayId/connectionId）；发送者当前设备不重复推送。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.mq", name = "enabled", havingValue = "true")
public class GatewayRouter {

    private final PresenceRepository presenceRepository;

    public Map<String, List<String>> route(MessageCreatedEvent event) {
        Set<Long> users = new LinkedHashSet<>(event.recipientUserIds());
        users.add(event.senderId());
        Map<String, List<String>> routes = new ConcurrentHashMap<>();
        for (Long userId : users) {
            for (PresenceRecord record : presenceRepository.findByUserId(userId)) {
                if (record.status() != PresenceStatus.ONLINE) {
                    continue;
                }
                if (userId == event.senderId() && event.senderDeviceId() != null
                        && event.senderDeviceId().equals(record.deviceId())) {
                    continue;
                }
                routes.computeIfAbsent(record.gatewayId(), key -> new ArrayList<>())
                        .add(record.connectionId());
            }
        }
        return routes;
    }
}
