package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 转让群主请求。
 */
public record TransferGroupRequest(@NotNull Long newOwnerId) {
}
