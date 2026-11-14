package com.veryal.domain.device;

/**
 * 客户端平台。未知平台不阻断登录，但会记录为 UNKNOWN。
 */
public enum Platform {

    WINDOWS,
    MACOS,
    LINUX,
    ANDROID,
    IOS,
    WEB,
    UNKNOWN;

    public static Platform from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return Platform.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
