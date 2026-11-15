package com.veryal.domain.tenant;

import java.util.List;

/**
 * 租户管理员仓储。
 */
public interface TenantAdminRepository {

    boolean exists(long tenantId, long userId);

    List<TenantAdmin> list(long tenantId);

    void save(TenantAdmin admin);

    void delete(long tenantId, long userId);

    long count(long tenantId);
}
