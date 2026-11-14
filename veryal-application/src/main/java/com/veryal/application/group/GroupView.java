package com.veryal.application.group;

import java.time.Instant;
import java.util.List;

/**
 * 群详情视图。
 */
public record GroupView(String groupId, String name, long ownerId, List<Long> memberIds,
                        Instant createdAt, Instant updatedAt) {
}
