package com.veryal.application.group;

import java.time.Instant;

/**
 * 群列表项。
 */
public record GroupSummary(String groupId, String name, long ownerId, long memberCount, Instant updatedAt) {
}
