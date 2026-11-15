package com.veryal.domain.audit;

import java.util.List;

/**
 * 审计仓储端口。
 */
public interface AuditRepository {

    void append(AuditLog log);

    List<AuditLog> findByOperator(long operatorId, int limit);

    long countByAction(String action);
}
