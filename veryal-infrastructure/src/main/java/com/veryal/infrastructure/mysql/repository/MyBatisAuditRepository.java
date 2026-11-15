package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.audit.AuditLog;
import com.veryal.domain.audit.AuditRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.AuditLogMapper;
import com.veryal.infrastructure.mysql.po.AuditLogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审计仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisAuditRepository implements AuditRepository {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void append(AuditLog log) {
        AuditLogPO po = new AuditLogPO();
        po.id = log.id();
        po.operatorId = log.operatorId();
        po.action = log.action();
        po.targetType = log.targetType();
        po.targetId = log.targetId();
        po.detail = log.detail();
        po.createdAt = SqlTime.toLocal(log.createdAt());
        auditLogMapper.insert(po);
    }

    @Override
    public List<AuditLog> findByOperator(long operatorId, int limit) {
        return auditLogMapper.selectList(Wrappers.<AuditLogPO>lambdaQuery()
                        .eq(AuditLogPO::getOperatorId, operatorId)
                        .orderByDesc(AuditLogPO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByAction(String action) {
        Long count = auditLogMapper.selectCount(Wrappers.<AuditLogPO>lambdaQuery()
                .eq(AuditLogPO::getAction, action));
        return count == null ? 0L : count;
    }

    private AuditLog toDomain(AuditLogPO po) {
        return new AuditLog(po.id, po.operatorId, po.action,
                po.targetType, po.targetId, po.detail, SqlTime.toInstant(po.createdAt));
    }
}
