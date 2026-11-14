package com.veryal.domain.identity;

/**
 * 用户状态。
 */
public enum UserStatus {

    DISABLED(0),
    ACTIVE(1);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static UserStatus fromCode(int code) {
        return code == ACTIVE.code ? ACTIVE : DISABLED;
    }
}
