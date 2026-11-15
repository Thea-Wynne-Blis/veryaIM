package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.common.core.TenantIds;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.FriendMapper;
import com.veryal.infrastructure.mysql.po.FriendPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 好友关系 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisFriendRepository implements FriendRepository {

    private final FriendMapper friendMapper;

    @Override
    public void save(Friend friend) {
        friendMapper.insert(toPO(friend));
    }

    @Override
    public Optional<Friend> find(long tenantId, long userId, long friendId) {
        return Optional.ofNullable(friendMapper.selectOne(Wrappers.<FriendPO>lambdaQuery()
                        .eq(FriendPO::getUserId, userId)
                        .eq(FriendPO::getFriendId, friendId)))
                .map(this::toDomain);
    }

    @Override
    public List<Friend> listByUser(long tenantId, long userId, int limit) {
        return friendMapper.selectList(Wrappers.<FriendPO>lambdaQuery()
                        .eq(FriendPO::getUserId, userId)
                        .orderByDesc(FriendPO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateRemark(long tenantId, long userId, long friendId, String remark) {
        friendMapper.update(null, Wrappers.<FriendPO>lambdaUpdate()
                .eq(FriendPO::getUserId, userId)
                .eq(FriendPO::getFriendId, friendId)
                .set(FriendPO::getRemark, remark));
    }

    @Override
    public void delete(long tenantId, long userId, long friendId) {
        friendMapper.delete(Wrappers.<FriendPO>lambdaQuery()
                .eq(FriendPO::getUserId, userId)
                .eq(FriendPO::getFriendId, friendId));
    }

    private Friend toDomain(FriendPO po) {
        return new Friend(po.id, TenantIds.DEFAULT_TENANT_ID, po.userId, po.friendId, po.remark,
                SqlTime.toInstant(po.createdAt));
    }

    private FriendPO toPO(Friend friend) {
        FriendPO po = new FriendPO();
        po.id = friend.id();
        po.userId = friend.userId();
        po.friendId = friend.friendId();
        po.remark = friend.remark();
        po.createdAt = SqlTime.toLocal(friend.createdAt());
        return po;
    }
}
