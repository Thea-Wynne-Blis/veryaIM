package com.veryal.domain.friend;

/**
 * 黑名单仓储。
 */
public interface BlacklistRepository {

    void save(long userId, long blockedUserId);

    void delete(long userId, long blockedUserId);

    boolean exists(long userId, long blockedUserId);

    java.util.List<Long> list(long userId, int limit);
}
