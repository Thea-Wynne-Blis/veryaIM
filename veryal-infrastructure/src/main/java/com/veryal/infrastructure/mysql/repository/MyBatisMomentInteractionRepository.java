package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.moment.MomentComment;
import com.veryal.domain.moment.MomentInteractionRepository;
import com.veryal.domain.moment.MomentLike;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.MomentCommentMapper;
import com.veryal.infrastructure.mysql.mapper.MomentLikeMapper;
import com.veryal.infrastructure.mysql.mapper.MomentMapper;
import com.veryal.infrastructure.mysql.po.MomentCommentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 点赞与评论的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisMomentInteractionRepository implements MomentInteractionRepository {

    private final MomentMapper momentMapper;
    private final MomentLikeMapper momentLikeMapper;
    private final MomentCommentMapper momentCommentMapper;

    @Override
    public Set<Long> likedMomentIds(long userId, Collection<Long> momentIds) {
        if (momentIds == null || momentIds.isEmpty()) {
            return Set.of();
        }
        return momentLikeMapper.selectLikedMomentIds(userId, momentIds).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<MomentLike> listLikers(Collection<Long> momentIds, int perMoment) {
        if (momentIds == null || momentIds.isEmpty()) {
            return List.of();
        }
        return momentLikeMapper.selectLikers(momentIds, Math.max(1, perMoment)).stream()
                .map(po -> new MomentLike(po.momentId, po.userId,
                        SqlTime.toInstant(po.createdAt)))
                .toList();
    }

    @Override
    public boolean addLike(long momentId, long userId, Instant createdAt) {
        try {
            return momentLikeMapper.insert(momentId, userId, SqlTime.toLocal(createdAt)) > 0;
        } catch (DuplicateKeyException e) {
            // 并发或重复点赞：保持幂等，不重复计数
            return false;
        }
    }

    @Override
    public boolean removeLike(long momentId, long userId) {
        return momentLikeMapper.delete(momentId, userId) > 0;
    }

    @Override
    public void addLikeCount(long momentId, int delta) {
        momentMapper.addLikeCount(momentId, delta);
    }

    @Override
    public void saveComment(MomentComment comment) {
        momentCommentMapper.insert(toPO(comment));
    }

    @Override
    public Optional<MomentComment> findComment(long commentId) {
        return Optional.ofNullable(momentCommentMapper.selectById(commentId))
                .map(this::toDomain);
    }

    @Override
    public List<MomentComment> listComments(long momentId, long cursor, int limit) {
        var query = Wrappers.<MomentCommentPO>lambdaQuery()
                .eq(MomentCommentPO::getMomentId, momentId)
                .isNull(MomentCommentPO::getDeletedAt)
                .orderByAsc(MomentCommentPO::getId)
                .last("LIMIT " + Math.max(1, limit));
        if (cursor > 0) {
            query.gt(MomentCommentPO::getId, cursor);
        }
        return momentCommentMapper.selectList(query).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<MomentComment> listFirstComments(Collection<Long> momentIds, int perMoment) {
        if (momentIds == null || momentIds.isEmpty()) {
            return List.of();
        }
        return momentCommentMapper.selectFirstComments(momentIds, Math.max(1, perMoment))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void softDeleteComment(long commentId, Instant deletedAt) {
        momentCommentMapper.update(null, Wrappers.<MomentCommentPO>lambdaUpdate()
                .eq(MomentCommentPO::getId, commentId)
                .isNull(MomentCommentPO::getDeletedAt)
                .set(MomentCommentPO::getDeletedAt, SqlTime.toLocal(deletedAt)));
    }

    @Override
    public void addCommentCount(long momentId, int delta) {
        momentMapper.addCommentCount(momentId, delta);
    }

    private MomentComment toDomain(MomentCommentPO po) {
        return new MomentComment(po.id, po.momentId, po.userId, po.replyToId,
                po.replyToUserId, po.content, SqlTime.toInstant(po.createdAt),
                SqlTime.toInstant(po.deletedAt));
    }

    private MomentCommentPO toPO(MomentComment comment) {
        MomentCommentPO po = new MomentCommentPO();
        po.id = comment.id();
        po.momentId = comment.momentId();
        po.userId = comment.userId();
        po.replyToId = comment.replyToId();
        po.replyToUserId = comment.replyToUserId();
        po.content = comment.content();
        po.createdAt = SqlTime.toLocal(comment.createdAt());
        po.deletedAt = SqlTime.toLocal(comment.deletedAt());
        return po;
    }
}
