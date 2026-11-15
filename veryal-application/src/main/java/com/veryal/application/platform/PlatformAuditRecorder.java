package com.veryal.application.platform;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.platform.PlatformAuditLog;
import com.veryal.domain.platform.PlatformAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台管理员操作审计，独立事务、失败不影响主操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAuditRecorder {

    private final PlatformAuditRepository platformAuditRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long adminId, String action, String targetType, String targetId, String detail) {
        try {
            platformAuditRepository.append(new PlatformAuditLog(idGenerator.nextId(), adminId, action,
                    targetType, targetId, detail, timeProvider.now()));
        } catch (RuntimeException e) {
            log.warn("platform audit failed: admin={}, action={}, target={}:{}", adminId, action,
                    targetType, targetId, e);
        }
    }
}
