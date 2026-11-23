package com.veryal.access.http.dto;

import com.veryal.application.chatbackground.ChatBackgroundView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前用户全部聊天背景：全局 + 会话级覆盖。
 */
public record ChatBackgroundResponse(
        @Schema(description = "全局背景")
        ChatBackgroundItem global,
        @Schema(description = "按会话 ID 索引的覆盖；未出现在这里表示跟随全局")
        Map<String, ChatBackgroundItem> conversations
) {

    public static ChatBackgroundResponse from(ChatBackgroundView view) {
        Map<String, ChatBackgroundItem> map = new LinkedHashMap<>();
        view.conversations().forEach((id, background) ->
                map.put(id, ChatBackgroundItem.from(background)));
        return new ChatBackgroundResponse(ChatBackgroundItem.from(view.global()), map);
    }
}
