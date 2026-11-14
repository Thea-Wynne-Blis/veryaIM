package com.veryal.access.http.dto;

import com.veryal.application.group.GroupSummary;

import java.time.Instant;

/**
 * 群列表项响应。
 */
public record GroupSummaryResponse(String groupId, String name, long ownerId, long memberCount,
                                   Instant updatedAt) {

    public static GroupSummaryResponse from(GroupSummary summary) {
        return new GroupSummaryResponse(summary.groupId(), summary.name(), summary.ownerId(),
                summary.memberCount(), summary.updatedAt());
    }
}
