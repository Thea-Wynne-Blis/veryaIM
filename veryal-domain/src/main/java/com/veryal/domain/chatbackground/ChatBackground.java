package com.veryal.domain.chatbackground;

/**
 * 聊天背景选择。
 *
 * <p>图片与预设互斥：{@code fileId} 表示用户上传的图片，{@code preset} 表示内置渐变。
 * 两者都为空表示「没有设置」——会话级为空时回退到用户的全局背景。</p>
 */
public record ChatBackground(Long fileId, String preset) {

    private static final ChatBackground NONE = new ChatBackground(null, null);

    public static ChatBackground none() {
        return NONE;
    }

    public static ChatBackground image(long fileId) {
        return new ChatBackground(fileId, null);
    }

    public static ChatBackground preset(String preset) {
        return new ChatBackground(null, preset);
    }

    public boolean isEmpty() {
        return fileId == null && (preset == null || preset.isBlank());
    }
}
