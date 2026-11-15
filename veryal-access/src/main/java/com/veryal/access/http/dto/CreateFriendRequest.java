package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送好友申请。
 */
public record CreateFriendRequest(@NotNull Long toUserId, @Size(max = 255) String message) {
}
