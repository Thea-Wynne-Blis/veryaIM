package com.veryal.domain.tenant;

/**
 * 租户状态。
 */
public enum TenantStatus {

    ACTIVE(0),
    DISABLED(1);

    private final int code;

    TenantStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static TenantStatus fromCode(int code) {
        return code == DISABLED.code ? DISABLED : ACTIVE;
    }
}
