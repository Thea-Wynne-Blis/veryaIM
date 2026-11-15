package com.veryal.common.core;

/**
 * 内部固定默认空间 ID。
 * 多租户产品能力已移除，此常量仅用于兼容历史 tenant_id 数据，后续迁移删除。
 */
public final class TenantIds {

    public static final long DEFAULT_TENANT_ID = 1L;

    private TenantIds() {
    }
}
