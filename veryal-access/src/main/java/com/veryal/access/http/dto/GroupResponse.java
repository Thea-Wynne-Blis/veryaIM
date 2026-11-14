package com.veryal.access.http.dto;

import com.veryal.application.group.GroupSummary;
import com.veryal.application.group.GroupView;

import java.time.Instant;
import java.util.List;

/**
 * 群详情响应。
 */
public record GroupResponse(String groupId, String name, long ownerId, List<Long> memberIds,
                            Instant createdAt, Instant updatedAt) {

    public static GroupResponse from(GroupView view) {
        return new GroupResponse(view.groupId(), view.name(), view.ownerId(), view.memberIds(),
                view.createdAt(), view.updatedAt());
    }

    public static GroupResponse from(GroupSummary summary) {
        return new GroupResponse(summary.groupId(), summary.name(), summary.ownerId(), List.of(),
                null, summary.updatedAt());
    }
}
