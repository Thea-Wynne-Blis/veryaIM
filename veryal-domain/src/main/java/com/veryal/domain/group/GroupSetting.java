package com.veryal.domain.group;

import java.time.Instant;

/**
 * 群设置：公告与全员禁言。
 */
public record GroupSetting(String groupId, long tenantId, String announcement, boolean muteAll,
                           Instant updatedAt, long updatedBy) {

    public static GroupSetting empty(String groupId, long tenantId) {
        return new GroupSetting(groupId, tenantId, null, false, Instant.EPOCH, 0L);
    }
}
