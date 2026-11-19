package com.veryal.domain.friend;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 好友关系仓储。
 */
public interface FriendRepository {

    void save(Friend friend);

    Optional<Friend> find(long userId, long friendId);

    List<Friend> listByUser(long userId, int limit);

    void updateRemark(long userId, long friendId, String remark);

    /** 批量取好友备注，信息流渲染作者名称时使用，避免逐条查询。 */
    Map<Long, String> remarks(long userId, Collection<Long> friendIds);

    void delete(long userId, long friendId);
}
