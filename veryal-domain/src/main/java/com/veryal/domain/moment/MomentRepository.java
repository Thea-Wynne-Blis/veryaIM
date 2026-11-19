package com.veryal.domain.moment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 动态、媒体与可见名单仓储。
 *
 * <p>信息流查询在仓储内一次完成「好友 + 可见性 + 作者时间范围」的过滤，
 * 应用层只负责把返回的动态补齐作者、点赞与评论。</p>
 */
public interface MomentRepository {

    void save(Moment moment);

    Optional<Moment> findById(long momentId);

    List<Moment> findByIds(Collection<Long> momentIds);

    /**
     * 信息流：包含自己发布的动态，按 ID 倒序游标分页。
     *
     * @param authorId 非空时只看该作者的主页；为空表示好友信息流
     * @param cursor   上一页最后一条动态 ID；首次加载传 {@code Long.MAX_VALUE}
     */
    List<Moment> feed(long viewerId, Long authorId, long cursor, int limit, Instant now);

    void saveMedia(List<MomentMedia> media);

    List<MomentMedia> listMedia(Collection<Long> momentIds);

    List<MomentMedia> listMedia(long momentId);

    Optional<MomentMedia> findMedia(long momentId, long fileId);

    void saveAudience(List<MomentAudience> audience);

    Set<Long> audienceUserIds(long momentId, MomentAudienceMode mode);

    void softDelete(long momentId, Instant deletedAt);

    void updateStatus(long momentId, MomentStatus status);
}
