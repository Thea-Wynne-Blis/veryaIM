package com.veryal.domain.chatbackground;

import java.util.Locale;
import java.util.Set;

/**
 * 内置聊天背景预设的白名单。
 *
 * <p>预设只是一组 CSS 渐变，不在数据库里存整段样式，避免客户端版本与历史数据不一致。
 * 这里的 ID 必须与前端 {@code utils/chatBackground.ts} 的 {@code CHAT_BACKGROUND_PRESETS}
 * 保持一致；新增预设时两边一起改。</p>
 */
public final class ChatBackgroundPresets {

    private static final Set<String> IDS = Set.of(
            "mist", "sand", "sage", "blush", "dusk", "graphite");

    private ChatBackgroundPresets() {
    }

    /** 归一化为小写；空值返回 {@code null}。 */
    public static String normalize(String preset) {
        if (preset == null) {
            return null;
        }
        String id = preset.trim().toLowerCase(Locale.ROOT);
        return id.isEmpty() ? null : id;
    }

    public static boolean isKnown(String preset) {
        return preset != null && IDS.contains(preset);
    }
}
