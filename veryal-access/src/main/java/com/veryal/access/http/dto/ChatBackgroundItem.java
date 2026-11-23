package com.veryal.access.http.dto;

import com.veryal.domain.chatbackground.ChatBackground;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一个聊天背景：图片与预设互斥，都为空表示「未设置」。
 */
public record ChatBackgroundItem(
        @Schema(description = "背景图片文件 ID；前端用带鉴权的 /files/{id}/content 读取")
        Long fileId,
        @Schema(description = "内置预设 ID，例如 mist / sand / sage")
        String preset
) {

    public static ChatBackgroundItem from(ChatBackground background) {
        return new ChatBackgroundItem(background.fileId(), background.preset());
    }
}
