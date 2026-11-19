package com.veryal.domain.moment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 点赞与评论仓储。
 *
 * <p>点赞是幂等的：重复点赞返回 {@code false}，只有真正插入成功时调用方才递增计数。
 * 评论与点赞计数走原子 UPDATE，读取信息流时不执行 COUNT(*)。</p>
 */
public interface MomentInteractionRepository {

    Set<Long> likedMomentIds(long userId, Collection<Long> momentIds);

    /** 每条动态取最近的若干点赞人，信息流一次取回，避免逐条查询。 */
    List<MomentLike> listLikers(Collection<Long> momentIds, int perMoment);

    boolean addLike(long momentId, long userId, Instant createdAt);

    boolean removeLike(long momentId, long userId);

    void addLikeCount(long momentId, int delta);

    void saveComment(MomentComment comment);

    Optional<MomentComment> findComment(long commentId);

    List<MomentComment> listComments(long momentId, long cursor, int limit);

    /** 每条动态取最早的若干条评论，信息流一次取回。 */
    List<MomentComment> listFirstComments(Collection<Long> momentIds, int perMoment);

    void softDeleteComment(long commentId, Instant deletedAt);

    void addCommentCount(long momentId, int delta);
}
