package com.veryal.application.chatbackground;

import com.veryal.domain.chatbackground.ChatBackground;

import java.util.Map;

/**
 * 用户当前的聊天背景：全局一份，以及各会话的覆盖。
 */
public record ChatBackgroundView(ChatBackground global,
                                 Map<String, ChatBackground> conversations) {
}
