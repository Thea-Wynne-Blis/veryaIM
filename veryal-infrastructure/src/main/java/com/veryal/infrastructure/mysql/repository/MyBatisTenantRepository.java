package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.tenant.Tenant;
import com.veryal.domain.tenant.TenantRepository;
import com.veryal.domain.tenant.TenantStatus;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.TenantMapper;
import com.veryal.infrastructure.mysql.po.TenantPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 租户仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisTenantRepository implements TenantRepository {

    private final TenantMapper tenantMapper;

    @Override
    public Optional<Tenant> findById(long tenantId) {
        return Optional.ofNullable(tenantMapper.selectById(tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<Tenant> findByCode(String code) {
        return Optional.ofNullable(tenantMapper.selectOne(Wrappers.<TenantPO>lambdaQuery()
                .eq(TenantPO::getCode, code))).map(this::toDomain);
    }

    @Override
    public List<Tenant> findAll(int limit) {
        return tenantMapper.selectList(Wrappers.<TenantPO>lambdaQuery()
                        .orderByAsc(TenantPO::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(Tenant tenant) {
        tenantMapper.insert(toPO(tenant));
    }

    @Override
    public void update(Tenant tenant) {
        tenantMapper.updateById(toPO(tenant));
    }

    private Tenant toDomain(TenantPO po) {
        return new Tenant(po.id, po.code, po.name, TenantStatus.fromCode(po.status),
                po.maxUsers, po.maxGroups, po.maxStorageBytes,
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private TenantPO toPO(Tenant tenant) {
        TenantPO po = new TenantPO();
        po.id = tenant.id();
        po.code = tenant.code();
        po.name = tenant.name();
        po.status = tenant.status().code();
        po.maxUsers = tenant.maxUsers();
        po.maxGroups = tenant.maxGroups();
        po.maxStorageBytes = tenant.maxStorageBytes();
        po.createdAt = SqlTime.toLocal(tenant.createdAt());
        po.updatedAt = SqlTime.toLocal(tenant.updatedAt());
        return po;
    }
}
