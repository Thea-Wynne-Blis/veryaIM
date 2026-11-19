package com.veryal.domain.moment;

/**
 * 动态媒体类型。
 */
public enum MomentMediaKind {

    IMAGE(1),
    VIDEO(2);

    private final int code;

    MomentMediaKind(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MomentMediaKind fromCode(int code) {
        return code == VIDEO.code ? VIDEO : IMAGE;
    }

    public static MomentMediaKind parse(String value) {
        return "VIDEO".equalsIgnoreCase(value) ? VIDEO : IMAGE;
    }
}
