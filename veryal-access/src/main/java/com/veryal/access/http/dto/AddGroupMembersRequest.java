package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 添加群成员请求。
 */
public record AddGroupMembersRequest(@NotEmpty List<Long> userIds) {
}
