package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.common.core.TenantIds;
import com.veryal.domain.friend.FriendRequest;
import com.veryal.domain.friend.FriendRequestRepository;
import com.veryal.domain.friend.FriendRequestStatus;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.FriendRequestMapper;
import com.veryal.infrastructure.mysql.po.FriendRequestPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 好友申请 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisFriendRequestRepository implements FriendRequestRepository {

    private final FriendRequestMapper friendRequestMapper;

    @Override
    public void save(FriendRequest request) {
        friendRequestMapper.insert(toPO(request));
    }

    @Override
    public void update(FriendRequest request) {
        friendRequestMapper.update(null, Wrappers.<FriendRequestPO>lambdaUpdate()
                .eq(FriendRequestPO::getId, request.id())
                .set(FriendRequestPO::getMessage, request.message())
                .set(FriendRequestPO::getStatus, request.status().code())
                .set(FriendRequestPO::getUpdatedAt, SqlTime.toLocal(request.updatedAt())));
    }

    @Override
    public Optional<FriendRequest> findById(long tenantId, long requestId) {
        return Optional.ofNullable(friendRequestMapper.selectOne(Wrappers.<FriendRequestPO>lambdaQuery()
                        .eq(FriendRequestPO::getId, requestId)))
                .map(this::toDomain);
    }

    @Override
    public Optional<FriendRequest> findBetween(long tenantId, long fromUserId, long toUserId) {
        return Optional.ofNullable(friendRequestMapper.selectOne(Wrappers.<FriendRequestPO>lambdaQuery()
                        .eq(FriendRequestPO::getFromUserId, fromUserId)
                        .eq(FriendRequestPO::getToUserId, toUserId)))
                .map(this::toDomain);
    }

    @Override
    public List<FriendRequest> listIncoming(long tenantId, long userId, int limit) {
        return list(tenantId, "to_user_id", userId, limit);
    }

    @Override
    public List<FriendRequest> listOutgoing(long tenantId, long userId, int limit) {
        return list(tenantId, "from_user_id", userId, limit);
    }

    @Override
    public long countPendingIncoming(long tenantId, long userId) {
        Long count = friendRequestMapper.selectCount(Wrappers.<FriendRequestPO>lambdaQuery()
                .eq(FriendRequestPO::getToUserId, userId)
                .eq(FriendRequestPO::getStatus, FriendRequestStatus.PENDING.code()));
        return count == null ? 0 : count;
    }

    private List<FriendRequest> list(long tenantId, String column, long userId, int limit) {
        var query = Wrappers.<FriendRequestPO>lambdaQuery()
                .eq(FriendRequestPO::getStatus, FriendRequestStatus.PENDING.code())
                .orderByDesc(FriendRequestPO::getUpdatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if ("to_user_id".equals(column)) {
            query.eq(FriendRequestPO::getToUserId, userId);
        } else {
            query.eq(FriendRequestPO::getFromUserId, userId);
        }
        return friendRequestMapper.selectList(query).stream().map(this::toDomain).toList();
    }

    private FriendRequest toDomain(FriendRequestPO po) {
        return new FriendRequest(po.id, TenantIds.DEFAULT_TENANT_ID, po.fromUserId, po.toUserId,
                po.message, FriendRequestStatus.fromCode(po.status),
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private FriendRequestPO toPO(FriendRequest request) {
        FriendRequestPO po = new FriendRequestPO();
        po.id = request.id();
        po.fromUserId = request.fromUserId();
        po.toUserId = request.toUserId();
        po.message = request.message();
        po.status = request.status().code();
        po.createdAt = SqlTime.toLocal(request.createdAt());
        po.updatedAt = SqlTime.toLocal(request.updatedAt());
        return po;
    }
}
