package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.identity.UserStatus;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.UserMapper;
import com.veryal.infrastructure.mysql.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(long userId) {
        return Optional.ofNullable(userMapper.selectById(userId)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getUsername, username)))
                .map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(Wrappers.<UserPO>lambdaQuery()
                .eq(UserPO::getUsername, username));
        return count != null && count > 0;
    }

    @Override
    public void save(User user) {
        userMapper.insert(toPO(user));
    }

    @Override
    public void update(User user) {
        userMapper.updateById(toPO(user));
    }

    @Override
    public long count() {
        Long count = userMapper.selectCount(Wrappers.<UserPO>lambdaQuery());
        return count == null ? 0L : count;
    }

    @Override
    public List<User> findAll(int limit) {
        return userMapper.selectList(Wrappers.<UserPO>lambdaQuery()
                        .orderByAsc(UserPO::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 10000))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<User> search(String keyword, int limit) {
        String value = keyword == null ? "" : keyword.trim();
        return userMapper.selectList(Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getStatus, UserStatus.ACTIVE.code())
                        .and(wrapper -> wrapper
                                .like(UserPO::getUsername, value)
                                .or()
                                .like(UserPO::getNickname, value))
                        .orderByAsc(UserPO::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<User> searchAll(String keyword, int limit) {
        String value = keyword == null ? "" : keyword.trim();
        return userMapper.selectList(Wrappers.<UserPO>lambdaQuery()
                        .and(wrapper -> wrapper
                                .like(UserPO::getUsername, value)
                                .or()
                                .like(UserPO::getNickname, value))
                        .orderByAsc(UserPO::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private User toDomain(UserPO po) {
        return new User(po.id, po.username, po.nickname, po.avatar,
                UserStatus.fromCode(po.status), po.passwordHash,
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private UserPO toPO(User user) {
        UserPO po = new UserPO();
        po.id = user.id();
        po.username = user.username();
        po.nickname = user.nickname();
        po.avatar = user.avatar();
        po.status = user.status().code();
        po.passwordHash = user.passwordHash();
        po.createdAt = SqlTime.toLocal(user.createdAt());
        po.updatedAt = SqlTime.toLocal(user.updatedAt());
        return po;
    }
}
