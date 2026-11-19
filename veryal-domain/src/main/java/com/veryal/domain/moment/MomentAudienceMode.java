package com.veryal.domain.moment;

/**
 * 可见名单模式：允许 / 排除。
 */
public enum MomentAudienceMode {

    ALLOW(1),
    DENY(2);

    private final int code;

    MomentAudienceMode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
