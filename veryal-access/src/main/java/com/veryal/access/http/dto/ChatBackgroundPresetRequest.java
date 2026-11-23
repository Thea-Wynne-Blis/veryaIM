package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 选择内置背景。{@code conversationId} 为空表示改全局背景。
 */
public record ChatBackgroundPresetRequest(
        @Schema(description = "会话 ID；不传表示全局背景")
        String conversationId,
        @Schema(description = "预设 ID，例如 mist / sand / sage")
        String preset
) {
}
