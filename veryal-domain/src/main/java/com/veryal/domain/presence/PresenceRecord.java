package com.veryal.domain.presence;

import java.time.Instant;

/**
 * 单个设备连接的在线状态快照。
 *
 * <p>同一用户可以有多条记录（多端在线）；Channel 只存在于 Gateway 本地注册表，不写入 Redis。</p>
 */
public record PresenceRecord(long userId, long tenantId, String deviceId, String gatewayId,
                             String connectionId, Instant lastHeartbeat, PresenceStatus status) {

    public PresenceRecord {
        if (userId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connectionId must not be blank");
        }
        if (status == null) {
            status = PresenceStatus.ONLINE;
        }
        if (lastHeartbeat == null) {
            lastHeartbeat = Instant.now();
        }
    }

    public static PresenceRecord online(long userId, long tenantId, String deviceId, String gatewayId,
                                        String connectionId, Instant now) {
        return new PresenceRecord(userId, tenantId, deviceId, gatewayId, connectionId, now, PresenceStatus.ONLINE);
    }
}
