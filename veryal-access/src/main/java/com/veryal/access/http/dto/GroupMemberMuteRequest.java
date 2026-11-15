package com.veryal.access.http.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 成员禁言分钟数，0 表示解除。
 */
public record GroupMemberMuteRequest(@Min(0) @Max(10080) int minutes) {
}
