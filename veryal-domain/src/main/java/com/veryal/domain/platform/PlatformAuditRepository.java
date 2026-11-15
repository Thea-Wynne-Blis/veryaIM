package com.veryal.domain.platform;

import java.util.List;

/**
 * 平台审计仓储。
 */
public interface PlatformAuditRepository {

    void append(PlatformAuditLog log);

    List<PlatformAuditLog> list(int limit);
}
