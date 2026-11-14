package com.veryal.common.core;

/**
 * 租户常量。V1 默认单租户，保留多租户字段与隔离能力。
 */
public final class TenantIds {

    public static final long DEFAULT_TENANT_ID = 1L;

    private TenantIds() {
    }
}
