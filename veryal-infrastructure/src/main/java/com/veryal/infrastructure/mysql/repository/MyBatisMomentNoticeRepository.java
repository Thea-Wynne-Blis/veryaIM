package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.moment.MomentNotice;
import com.veryal.domain.moment.MomentNoticeRepository;
import com.veryal.domain.moment.MomentNoticeType;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.MomentNoticeMapper;
import com.veryal.infrastructure.mysql.po.MomentNoticePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 互动通知的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisMomentNoticeRepository implements MomentNoticeRepository {

    private final MomentNoticeMapper momentNoticeMapper;

    @Override
    public void save(MomentNotice notice) {
        MomentNoticePO po = new MomentNoticePO();
        po.id = notice.id();
        po.userId = notice.userId();
        po.actorId = notice.actorId();
        po.type = notice.type().code();
        po.momentId = notice.momentId();
        po.commentId = notice.commentId();
        po.readAt = SqlTime.toLocal(notice.readAt());
        po.createdAt = SqlTime.toLocal(notice.createdAt());
        momentNoticeMapper.insert(po);
    }

    @Override
    public List<MomentNotice> list(long userId, long cursor, int limit) {
        var query = Wrappers.<MomentNoticePO>lambdaQuery()
                .eq(MomentNoticePO::getUserId, userId)
                .orderByDesc(MomentNoticePO::getId)
                .last("LIMIT " + Math.max(1, limit));
        if (cursor > 0) {
            query.lt(MomentNoticePO::getId, cursor);
        }
        return momentNoticeMapper.selectList(query).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long unreadCount(long userId) {
        return momentNoticeMapper.selectCount(Wrappers.<MomentNoticePO>lambdaQuery()
                .eq(MomentNoticePO::getUserId, userId)
                .isNull(MomentNoticePO::getReadAt));
    }

    @Override
    public void markAllRead(long userId, Instant readAt) {
        momentNoticeMapper.markAllRead(userId, SqlTime.toLocal(readAt));
    }

    @Override
    public void deleteByMoment(long momentId) {
        momentNoticeMapper.delete(Wrappers.<MomentNoticePO>lambdaQuery()
                .eq(MomentNoticePO::getMomentId, momentId));
    }

    @Override
    public void deleteByComment(long commentId) {
        momentNoticeMapper.delete(Wrappers.<MomentNoticePO>lambdaQuery()
                .eq(MomentNoticePO::getCommentId, commentId));
    }

    private MomentNotice toDomain(MomentNoticePO po) {
        return new MomentNotice(po.id, po.userId, po.actorId,
                MomentNoticeType.fromCode(po.type), po.momentId, po.commentId,
                SqlTime.toInstant(po.readAt), SqlTime.toInstant(po.createdAt));
    }
}
