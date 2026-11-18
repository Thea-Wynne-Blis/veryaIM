package com.veryal.domain.presence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 在线状态仓储端口。Redis 实现必须对连接身份做校验，避免旧连接误删新连接。
 */
public interface PresenceRepository {

    void markOnline(PresenceRecord record);

    /**
     * 心跳续期。返回是否续期成功：记录不存在（被 TTL 清掉）或 connectionId 不匹配时返回 false，
     * 调用方可据此决定是否重建在线状态。
     */
    boolean heartbeat(long userId, String deviceId, String connectionId, Instant now);

    void markOffline(long userId, String deviceId, String connectionId);

    List<PresenceRecord> findByUserId(long userId);

    boolean isOnline(long userId);

    /**
     * 批量判断在线：群成员列表一次取回，避免逐人往返 Redis。
     */
    Set<Long> onlineUserIds(Collection<Long> userIds);
}
