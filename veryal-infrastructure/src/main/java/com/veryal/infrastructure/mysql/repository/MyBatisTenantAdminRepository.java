package com.veryal.infrastructure.mysql.repository;

import com.veryal.common.core.TimeProvider;
import com.veryal.domain.tenant.TenantAdmin;
import com.veryal.domain.tenant.TenantAdminRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.TenantAdminMapper;
import com.veryal.infrastructure.mysql.po.TenantAdminPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 租户管理员 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisTenantAdminRepository implements TenantAdminRepository {

    private final TenantAdminMapper tenantAdminMapper;
    private final TimeProvider timeProvider;

    @Override
    public boolean exists(long tenantId, long userId) {
        return tenantAdminMapper.countOne(tenantId, userId) > 0;
    }

    @Override
    public List<TenantAdmin> list(long tenantId) {
        return tenantAdminMapper.selectByTenant(tenantId).stream()
                .map(po -> new TenantAdmin(po.tenantId, po.userId,
                        SqlTime.toInstant(po.createdAt)))
                .toList();
    }

    @Override
    public void save(TenantAdmin admin) {
        TenantAdminPO po = new TenantAdminPO();
        po.tenantId = admin.tenantId();
        po.userId = admin.userId();
        po.createdAt = SqlTime.toLocal(admin.createdAt() == null
                ? timeProvider.now() : admin.createdAt());
        tenantAdminMapper.insert(po);
    }

    @Override
    public void delete(long tenantId, long userId) {
        tenantAdminMapper.delete(tenantId, userId);
    }

    @Override
    public long count(long tenantId) {
        return tenantAdminMapper.countByTenant(tenantId);
    }
}
