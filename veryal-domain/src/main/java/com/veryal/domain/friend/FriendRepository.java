package com.veryal.domain.friend;

import java.util.List;
import java.util.Optional;

/**
 * 好友关系仓储。
 */
public interface FriendRepository {

    void save(Friend friend);

    Optional<Friend> find(long userId, long friendId);

    List<Friend> listByUser(long userId, int limit);

    void updateRemark(long userId, long friendId, String remark);

    void delete(long userId, long friendId);
}
