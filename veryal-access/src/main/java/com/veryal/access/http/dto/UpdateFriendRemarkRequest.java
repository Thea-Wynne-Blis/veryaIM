package com.veryal.access.http.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改好友备注。
 */
public record UpdateFriendRemarkRequest(@Size(max = 64) String remark) {
}
