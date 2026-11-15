package com.veryal.domain.tenant;

/**
 * 租户数据硬删除端口（由 infrastructure 实现）。
 */
public interface TenantDataPurger {

    void purge(long tenantId);
}
