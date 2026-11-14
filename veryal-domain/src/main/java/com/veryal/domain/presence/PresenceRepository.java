package com.veryal.domain.presence;

import java.time.Instant;
import java.util.List;

/**
 * 在线状态仓储端口。Redis 实现必须对连接身份做校验，避免旧连接误删新连接。
 */
public interface PresenceRepository {

    void markOnline(PresenceRecord record);

    void heartbeat(long userId, String deviceId, String connectionId, Instant now);

    void markOffline(long userId, String deviceId, String connectionId);

    List<PresenceRecord> findByUserId(long userId);

    boolean isOnline(long userId);
}
