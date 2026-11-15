package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.friend.BlacklistRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.BlacklistMapper;
import com.veryal.infrastructure.mysql.po.BlacklistPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 黑名单 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisBlacklistRepository implements BlacklistRepository {

    private final BlacklistMapper blacklistMapper;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Override
    public void save(long userId, long blockedUserId) {
        BlacklistPO po = new BlacklistPO();
        po.id = idGenerator.nextId();
        po.userId = userId;
        po.blockedUserId = blockedUserId;
        po.createdAt = SqlTime.toLocal(timeProvider.now());
        blacklistMapper.insert(po);
    }

    @Override
    public void delete(long userId, long blockedUserId) {
        blacklistMapper.delete(Wrappers.<BlacklistPO>lambdaQuery()
                .eq(BlacklistPO::getUserId, userId)
                .eq(BlacklistPO::getBlockedUserId, blockedUserId));
    }

    @Override
    public boolean exists(long userId, long blockedUserId) {
        Long count = blacklistMapper.selectCount(Wrappers.<BlacklistPO>lambdaQuery()
                .eq(BlacklistPO::getUserId, userId)
                .eq(BlacklistPO::getBlockedUserId, blockedUserId));
        return count != null && count > 0;
    }

    @Override
    public List<Long> list(long userId, int limit) {
        return blacklistMapper.selectList(Wrappers.<BlacklistPO>lambdaQuery()
                        .eq(BlacklistPO::getUserId, userId)
                        .orderByDesc(BlacklistPO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(po -> po.blockedUserId)
                .toList();
    }
}
