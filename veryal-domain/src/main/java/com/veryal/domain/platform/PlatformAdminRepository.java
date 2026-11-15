package com.veryal.domain.platform;

import java.time.Instant;
import java.util.Optional;

/**
 * 平台管理员仓储。
 */
public interface PlatformAdminRepository {

    Optional<PlatformAdmin> findById(long adminId);

    Optional<PlatformAdmin> findByUsername(String username);

    java.util.List<PlatformAdmin> findAll(int limit);

    void save(PlatformAdmin admin);

    void update(PlatformAdmin admin);

    void updateLastLogin(long adminId, Instant at);
}
