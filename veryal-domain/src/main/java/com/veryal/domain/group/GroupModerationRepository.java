package com.veryal.domain.group;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 群公告/禁言仓储端口。
 */
public interface GroupModerationRepository {

    Optional<GroupSetting> findSetting(String groupId);

    void saveSetting(GroupSetting setting);

    /**
     * 查询单个成员的禁言状态；无记录表示未禁言。
     */
    Optional<GroupMute> findMute(String groupId, long userId);

    /**
     * 查询群内全部禁言记录（成员列表一次取回，避免 N 次查询）。
     */
    List<GroupMute> findMutes(String groupId);

    /**
     * 写入禁言。
     *
     * @param mutedUntil {@code null} 表示永久禁言
     */
    void mute(String groupId, long userId, Instant mutedUntil, Instant now);

    void unmute(String groupId, long userId);

    /**
     * 解散群时清掉公告与全部禁言记录，避免留下孤儿数据。
     */
    void deleteGroupState(String groupId);
}
