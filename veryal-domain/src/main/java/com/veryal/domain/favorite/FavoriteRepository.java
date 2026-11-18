package com.veryal.domain.favorite;

import java.util.List;
import java.util.Optional;

/**
 * 收藏仓储。收藏只对本人可见，按创建时间倒序分页。
 */
public interface FavoriteRepository {

    void save(Favorite favorite);

    Optional<Favorite> find(long userId, long messageId);

    Optional<Favorite> findById(long id);

    List<Favorite> listByUser(long userId, int limit);

    void delete(long userId, long favoriteId);
}
