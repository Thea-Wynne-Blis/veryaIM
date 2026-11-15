package com.veryal.domain.friend;

/**
 * 黑名单仓储。
 */
public interface BlacklistRepository {

    void save(long tenantId, long userId, long blockedUserId);

    void delete(long tenantId, long userId, long blockedUserId);

    boolean exists(long tenantId, long userId, long blockedUserId);

    java.util.List<Long> list(long tenantId, long userId, int limit);
}
