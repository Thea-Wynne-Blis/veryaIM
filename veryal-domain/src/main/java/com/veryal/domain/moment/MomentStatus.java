package com.veryal.domain.moment;

/**
 * 动态状态。下架后对作者以外的人不可见。
 */
public enum MomentStatus {

    NORMAL(0),
    HIDDEN(1);

    private final int code;

    MomentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MomentStatus fromCode(int code) {
        return code == HIDDEN.code ? HIDDEN : NORMAL;
    }
}
