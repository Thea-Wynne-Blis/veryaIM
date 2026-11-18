package com.veryal.access.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 收藏一条消息。
 */
public record CreateFavoriteRequest(
        @NotBlank String conversationId,
        @Positive long messageId
) {
}
