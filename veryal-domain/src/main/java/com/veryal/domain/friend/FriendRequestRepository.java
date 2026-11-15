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

    Optional<FriendRequest> findById(long requestId);

    Optional<FriendRequest> findBetween(long fromUserId, long toUserId);

    List<FriendRequest> listIncoming(long userId, int limit);

    List<FriendRequest> listOutgoing(long userId, int limit);

    long countPendingIncoming(long userId);
}
