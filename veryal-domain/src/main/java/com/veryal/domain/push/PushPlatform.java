package com.veryal.domain.push;

/**
 * 推送设备平台。
 */
public enum PushPlatform {

    IOS,
    ANDROID,
    WEB;

    public static PushPlatform from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("platform is required");
        }
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "IOS", "IOS_APP", "APPLE" -> IOS;
            case "ANDROID", "ANDROID_APP" -> ANDROID;
            case "WEB", "WEB_PUSH" -> WEB;
            default -> throw new IllegalArgumentException("unsupported push platform");
        };
    }
}
