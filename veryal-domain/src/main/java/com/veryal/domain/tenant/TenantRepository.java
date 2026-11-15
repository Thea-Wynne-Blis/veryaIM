package com.veryal.domain.tenant;

import java.util.List;
import java.util.Optional;

/**
 * 租户仓储端口。
 */
public interface TenantRepository {

    Optional<Tenant> findById(long tenantId);

    Optional<Tenant> findByCode(String code);

    List<Tenant> findAll(int limit);

    void save(Tenant tenant);

    void update(Tenant tenant);
}
