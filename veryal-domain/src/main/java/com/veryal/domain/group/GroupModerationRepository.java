package com.veryal.domain.group;

import java.time.Instant;
import java.util.Optional;

/**
 * 群设置/禁言仓储。
 */
public interface GroupModerationRepository {

    Optional<GroupSetting> findSetting(String groupId);

    void saveSetting(GroupSetting setting);

    Optional<Instant> findMutedUntil(String groupId, long userId);

    void mute(String groupId, long userId, Instant until, Instant now);

    void unmute(String groupId, long userId);
}
