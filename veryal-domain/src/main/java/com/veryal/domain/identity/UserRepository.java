package com.veryal.domain.identity;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储端口，由 infrastructure-mysql 实现。
 */
public interface UserRepository {

    Optional<User> findById(long userId);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    void save(User user);

    void update(User user);

    long count();

    List<User> findAll(int limit);

    List<User> search(String keyword, int limit);

    List<User> searchAll(String keyword, int limit);
}
