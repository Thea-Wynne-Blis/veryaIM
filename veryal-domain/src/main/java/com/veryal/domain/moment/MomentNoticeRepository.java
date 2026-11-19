package com.veryal.domain.moment;

import java.time.Instant;
import java.util.List;

/**
 * 互动通知仓储。通知只服务点赞/评论，不做「好友发了新动态」的 fanout。
 */
public interface MomentNoticeRepository {

    void save(MomentNotice notice);

    List<MomentNotice> list(long userId, long cursor, int limit);

    long unreadCount(long userId);

    void markAllRead(long userId, Instant readAt);

    /** 动态被删除后清理指向它的通知，避免点进去是空页面。 */
    void deleteByMoment(long momentId);

    /** 评论被删除后清理对应通知。 */
    void deleteByComment(long commentId);
}
