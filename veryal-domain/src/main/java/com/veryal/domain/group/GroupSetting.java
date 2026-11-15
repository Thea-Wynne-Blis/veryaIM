package com.veryal.domain.group;

import java.time.Instant;

/**
 * 群设置：公告与全员禁言。
 */
public record GroupSetting(String groupId, String announcement, boolean muteAll,
                           Instant updatedAt, long updatedBy) {

    public static GroupSetting empty(String groupId) {
        return new GroupSetting(groupId, null, false, Instant.EPOCH, 0L);
    }
}
