package com.veryal.domain.identity;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储端口，由 infrastructure-mysql 实现。
 */
public interface UserRepository {

    Optional<User> findById(long userId);

    Optional<User> findByUsername(long tenantId, String username);

    boolean existsByUsername(long tenantId, String username);

    void save(User user);

    void update(User user);

    long count(long tenantId);

    List<User> findByTenant(long tenantId, int limit);

    List<User> search(long tenantId, String keyword, int limit);
}
