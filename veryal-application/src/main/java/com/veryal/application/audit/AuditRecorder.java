package com.veryal.application.audit;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.audit.AuditAction;
import com.veryal.domain.audit.AuditLog;
import com.veryal.domain.audit.AuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计记录器。
 *
 * <p>审计写入使用独立事务，失败只记录日志，不影响主业务事务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditRepository auditRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long operatorId, AuditAction action, String targetType,
                       String targetId, String detail) {
        try {
            auditRepository.append(new AuditLog(idGenerator.nextId(), operatorId,
                    action.name(), targetType, targetId, detail, timeProvider.now()));
        } catch (RuntimeException e) {
            log.warn("audit record failed: action={}, operator={}, target={}:{}", action, operatorId,
                    targetType, targetId, e);
        }
    }
}
