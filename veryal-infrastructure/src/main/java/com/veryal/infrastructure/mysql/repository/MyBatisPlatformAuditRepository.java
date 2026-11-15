package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.platform.PlatformAuditLog;
import com.veryal.domain.platform.PlatformAuditRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.PlatformAuditMapper;
import com.veryal.infrastructure.mysql.po.PlatformAuditLogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 平台审计 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisPlatformAuditRepository implements PlatformAuditRepository {

    private final PlatformAuditMapper platformAuditMapper;

    @Override
    public void append(PlatformAuditLog log) {
        PlatformAuditLogPO po = new PlatformAuditLogPO();
        po.id = log.id();
        po.adminId = log.adminId();
        po.action = log.action();
        po.targetType = log.targetType();
        po.targetId = log.targetId();
        po.detail = log.detail();
        po.createdAt = SqlTime.toLocal(log.createdAt());
        platformAuditMapper.insert(po);
    }

    @Override
    public List<PlatformAuditLog> list(int limit) {
        return platformAuditMapper.selectList(Wrappers.<PlatformAuditLogPO>lambdaQuery()
                        .orderByDesc(PlatformAuditLogPO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(po -> new PlatformAuditLog(po.id, po.adminId, po.action, po.targetType,
                        po.targetId, po.detail, SqlTime.toInstant(po.createdAt)))
                .toList();
    }
}
