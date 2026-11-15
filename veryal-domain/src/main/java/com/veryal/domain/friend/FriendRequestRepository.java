package com.veryal.domain.friend;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 好友申请仓储。
 */
public interface FriendRequestRepository {

    void save(FriendRequest request);

    void update(FriendRequest request);

    Optional<FriendRequest> findById(long tenantId, long requestId);

    Optional<FriendRequest> findBetween(long tenantId, long fromUserId, long toUserId);

    List<FriendRequest> listIncoming(long tenantId, long userId, int limit);

    List<FriendRequest> listOutgoing(long tenantId, long userId, int limit);

    long countPendingIncoming(long tenantId, long userId);
}
