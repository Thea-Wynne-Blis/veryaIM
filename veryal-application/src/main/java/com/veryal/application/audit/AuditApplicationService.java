package com.veryal.application.audit;

import com.veryal.domain.audit.AuditLog;
import com.veryal.domain.audit.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计查询：当前只开放用户查询自己的操作记录。
 */
@Service
@RequiredArgsConstructor
public class AuditApplicationService {

    private final AuditRepository auditRepository;

    public List<AuditLog> listMine(long tenantId, long userId, int limit) {
        int pageSize = limit <= 0 ? 50 : Math.min(limit, 200);
        return auditRepository.findByOperator(tenantId, userId, pageSize);
    }
}
