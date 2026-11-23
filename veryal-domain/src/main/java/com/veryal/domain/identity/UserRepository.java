package com.veryal.domain.identity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储端口，由 infrastructure-mysql 实现。
 */
public interface UserRepository {

    Optional<User> findById(long userId);

    /**
     * 头像文件 ID；未设置头像（或历史外链头像）时为空。
     */
    Optional<Long> findAvatarFileId(long userId);

    /**
     * 只更新头像两列，避免与资料编辑的整行更新互相覆盖。
     */
    void updateAvatar(long userId, String avatar, Long avatarFileId, Instant updatedAt);

    /**
     * 批量查询用户，用于群成员列表等一次取回多个资料，避免 N+1。
     */
    List<User> findByIds(Collection<Long> userIds);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    void save(User user);

    void update(User user);

    long count();

    List<User> findAll(int limit);

    List<User> search(String keyword, int limit);

    List<User> searchAll(String keyword, int limit);
}
