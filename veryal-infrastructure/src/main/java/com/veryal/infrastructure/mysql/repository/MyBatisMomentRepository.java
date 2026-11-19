package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.moment.Moment;
import com.veryal.domain.moment.MomentAudience;
import com.veryal.domain.moment.MomentAudienceMode;
import com.veryal.domain.moment.MomentMedia;
import com.veryal.domain.moment.MomentMediaKind;
import com.veryal.domain.moment.MomentRepository;
import com.veryal.domain.moment.MomentStatus;
import com.veryal.domain.moment.MomentVisibility;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.MomentAudienceMapper;
import com.veryal.infrastructure.mysql.mapper.MomentMapper;
import com.veryal.infrastructure.mysql.mapper.MomentMediaMapper;
import com.veryal.infrastructure.mysql.po.MomentAudiencePO;
import com.veryal.infrastructure.mysql.po.MomentMediaPO;
import com.veryal.infrastructure.mysql.po.MomentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态、媒体与可见名单的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisMomentRepository implements MomentRepository {

    private final MomentMapper momentMapper;
    private final MomentMediaMapper momentMediaMapper;
    private final MomentAudienceMapper momentAudienceMapper;

    @Override
    public void save(Moment moment) {
        momentMapper.insert(toPO(moment));
    }

    @Override
    public Optional<Moment> findById(long momentId) {
        return Optional.ofNullable(momentMapper.selectById(momentId)).map(this::toDomain);
    }

    @Override
    public List<Moment> findByIds(Collection<Long> momentIds) {
        if (momentIds == null || momentIds.isEmpty()) {
            return List.of();
        }
        return momentMapper.selectBatchIds(momentIds).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Moment> feed(long viewerId, Long authorId, long cursor, int limit, Instant now) {
        return momentMapper.selectFeed(viewerId, authorId, cursor, Math.max(1, limit),
                        SqlTime.toLocal(now)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void saveMedia(List<MomentMedia> media) {
        for (MomentMedia item : media) {
            momentMediaMapper.insert(toPO(item));
        }
    }

    @Override
    public List<MomentMedia> listMedia(Collection<Long> momentIds) {
        if (momentIds == null || momentIds.isEmpty()) {
            return List.of();
        }
        return momentMediaMapper.selectList(Wrappers.<MomentMediaPO>lambdaQuery()
                        .in(MomentMediaPO::getMomentId, momentIds)
                        .orderByAsc(MomentMediaPO::getMomentId)
                        .orderByAsc(MomentMediaPO::getSortOrder))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<MomentMedia> listMedia(long momentId) {
        return listMedia(List.of(momentId));
    }

    @Override
    public Optional<MomentMedia> findMedia(long momentId, long fileId) {
        return Optional.ofNullable(momentMediaMapper.selectOne(
                        Wrappers.<MomentMediaPO>lambdaQuery()
                                .eq(MomentMediaPO::getMomentId, momentId)
                                .eq(MomentMediaPO::getFileId, fileId)))
                .map(this::toDomain);
    }

    @Override
    public void saveAudience(List<MomentAudience> audience) {
        for (MomentAudience item : audience) {
            momentAudienceMapper.insert(item.momentId(), item.userId(), item.mode().code());
        }
    }

    @Override
    public Set<Long> audienceUserIds(long momentId, MomentAudienceMode mode) {
        return momentAudienceMapper.selectByMode(momentId, mode.code()).stream()
                .map(item -> item.userId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void softDelete(long momentId, Instant deletedAt) {
        momentMapper.update(null, Wrappers.<MomentPO>lambdaUpdate()
                .eq(MomentPO::getId, momentId)
                .isNull(MomentPO::getDeletedAt)
                .set(MomentPO::getDeletedAt, SqlTime.toLocal(deletedAt)));
    }

    @Override
    public void updateStatus(long momentId, MomentStatus status) {
        momentMapper.updateStatus(momentId, status.code());
    }

    private Moment toDomain(MomentPO po) {
        return new Moment(po.id, po.authorId, po.content,
                MomentVisibility.fromCode(po.visibility), po.likeCount, po.commentCount,
                MomentStatus.fromCode(po.status), SqlTime.toInstant(po.createdAt),
                SqlTime.toInstant(po.deletedAt));
    }

    private MomentPO toPO(Moment moment) {
        MomentPO po = new MomentPO();
        po.id = moment.id();
        po.authorId = moment.authorId();
        po.content = moment.content();
        po.visibility = moment.visibility().code();
        po.likeCount = moment.likeCount();
        po.commentCount = moment.commentCount();
        po.status = moment.status().code();
        po.createdAt = SqlTime.toLocal(moment.createdAt());
        po.deletedAt = SqlTime.toLocal(moment.deletedAt());
        return po;
    }

    private MomentMediaPO toPO(MomentMedia media) {
        MomentMediaPO po = new MomentMediaPO();
        po.id = media.id();
        po.momentId = media.momentId();
        po.fileId = media.fileId();
        po.sortOrder = media.sortOrder();
        po.kind = media.kind().code();
        po.width = media.width();
        po.height = media.height();
        po.durationSeconds = media.durationSeconds();
        return po;
    }

    private MomentMedia toDomain(MomentMediaPO po) {
        return new MomentMedia(po.id, po.momentId, po.fileId, po.sortOrder,
                MomentMediaKind.fromCode(po.kind), po.width == null ? 0 : po.width,
                po.height == null ? 0 : po.height, po.durationSeconds);
    }
}
