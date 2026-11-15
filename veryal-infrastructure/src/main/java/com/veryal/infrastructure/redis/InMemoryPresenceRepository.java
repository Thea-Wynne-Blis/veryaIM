package com.veryal.infrastructure.redis;

import com.veryal.domain.presence.PresenceRecord;
import com.veryal.domain.presence.PresenceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 单机内存在线状态实现，用于本地开发与测试，避免强依赖 Redis。
 */
@Repository
@ConditionalOnProperty(prefix = "veryal.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryPresenceRepository implements PresenceRepository {

    private final ConcurrentMap<Long, ConcurrentMap<String, PresenceRecord>> presence = new ConcurrentHashMap<>();

    @Override
    public void markOnline(PresenceRecord record) {
        presence.computeIfAbsent(record.userId(), id -> new ConcurrentHashMap<>())
                .put(record.deviceId(), record);
    }

    @Override
    public void heartbeat(long userId, String deviceId, String connectionId, Instant now) {
        Map<String, PresenceRecord> devices = presence.get(userId);
        if (devices == null) {
            return;
        }
        devices.computeIfPresent(deviceId, (key, current) -> current.connectionId().equals(connectionId)
                ? new PresenceRecord(current.userId(), current.deviceId(), current.gatewayId(),
                current.connectionId(), now, current.status())
                : current);
    }

    @Override
    public void markOffline(long userId, String deviceId, String connectionId) {
        Map<String, PresenceRecord> devices = presence.get(userId);
        if (devices == null) {
            return;
        }
        devices.computeIfPresent(deviceId, (key, current) ->
                current.connectionId().equals(connectionId) ? null : current);
        if (devices.isEmpty()) {
            presence.remove(userId, devices);
        }
    }

    @Override
    public List<PresenceRecord> findByUserId(long userId) {
        Map<String, PresenceRecord> devices = presence.get(userId);
        return devices == null ? List.of() : List.copyOf(devices.values());
    }

    @Override
    public boolean isOnline(long userId) {
        Map<String, PresenceRecord> devices = presence.get(userId);
        return devices != null && !devices.isEmpty();
    }
}
