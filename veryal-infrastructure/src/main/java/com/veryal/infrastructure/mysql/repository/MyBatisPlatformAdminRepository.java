package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.platform.PlatformAdmin;
import com.veryal.domain.platform.PlatformAdminRepository;
import com.veryal.domain.platform.PlatformAdminRole;
import com.veryal.domain.platform.PlatformAdminStatus;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.PlatformAdminMapper;
import com.veryal.infrastructure.mysql.po.PlatformAdminPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 平台管理员 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisPlatformAdminRepository implements PlatformAdminRepository {

    private final PlatformAdminMapper platformAdminMapper;

    @Override
    public Optional<PlatformAdmin> findById(long adminId) {
        return Optional.ofNullable(platformAdminMapper.selectById(adminId)).map(this::toDomain);
    }

    @Override
    public Optional<PlatformAdmin> findByUsername(String username) {
        return Optional.ofNullable(platformAdminMapper.selectOne(Wrappers.<PlatformAdminPO>lambdaQuery()
                .eq(PlatformAdminPO::getUsername, username))).map(this::toDomain);
    }

    @Override
    public void save(PlatformAdmin admin) {
        platformAdminMapper.insert(toPO(admin));
    }

    @Override
    public void updateLastLogin(long adminId, Instant at) {
        platformAdminMapper.update(null, Wrappers.<PlatformAdminPO>lambdaUpdate()
                .eq(PlatformAdminPO::getId, adminId)
                .set(PlatformAdminPO::getLastLoginAt, SqlTime.toLocal(at)));
    }

    private PlatformAdmin toDomain(PlatformAdminPO po) {
        return new PlatformAdmin(po.id, po.username, po.displayName, po.passwordHash,
                PlatformAdminRole.valueOf(po.role), po.status == 0
                ? PlatformAdminStatus.ACTIVE : PlatformAdminStatus.DISABLED,
                SqlTime.toInstant(po.createdAt),
                po.lastLoginAt == null ? null : SqlTime.toInstant(po.lastLoginAt));
    }

    private PlatformAdminPO toPO(PlatformAdmin admin) {
        PlatformAdminPO po = new PlatformAdminPO();
        po.id = admin.id();
        po.username = admin.username();
        po.displayName = admin.displayName();
        po.passwordHash = admin.passwordHash();
        po.role = admin.role().name();
        po.status = admin.status() == PlatformAdminStatus.ACTIVE ? 0 : 1;
        po.createdAt = SqlTime.toLocal(admin.createdAt());
        po.lastLoginAt = admin.lastLoginAt() == null ? null : SqlTime.toLocal(admin.lastLoginAt());
        return po;
    }
}
