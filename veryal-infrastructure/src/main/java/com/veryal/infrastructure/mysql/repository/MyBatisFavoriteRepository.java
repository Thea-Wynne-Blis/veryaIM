package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.favorite.Favorite;
import com.veryal.domain.favorite.FavoriteRepository;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.FavoriteMapper;
import com.veryal.infrastructure.mysql.po.FavoritePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 收藏仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisFavoriteRepository implements FavoriteRepository {

    private final FavoriteMapper favoriteMapper;

    @Override
    public void save(Favorite favorite) {
        favoriteMapper.insert(toPO(favorite));
    }

    @Override
    public Optional<Favorite> find(long userId, long messageId) {
        return Optional.ofNullable(favoriteMapper.selectOne(Wrappers.<FavoritePO>lambdaQuery()
                        .eq(FavoritePO::getUserId, userId)
                        .eq(FavoritePO::getMessageId, messageId)))
                .map(this::toDomain);
    }

    @Override
    public Optional<Favorite> findById(long id) {
        return Optional.ofNullable(favoriteMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Favorite> listByUser(long userId, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        return favoriteMapper.selectList(Wrappers.<FavoritePO>lambdaQuery()
                        .eq(FavoritePO::getUserId, userId)
                        .orderByDesc(FavoritePO::getCreatedAt)
                        .last("LIMIT " + size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(long userId, long favoriteId) {
        favoriteMapper.delete(Wrappers.<FavoritePO>lambdaQuery()
                .eq(FavoritePO::getUserId, userId)
                .eq(FavoritePO::getId, favoriteId));
    }

    private Favorite toDomain(FavoritePO po) {
        return new Favorite(po.id, po.userId, po.conversationId, po.messageId,
                po.msgType == null ? 0 : po.msgType, po.content, po.fileId, po.fileName,
                po.sizeBytes, po.contentType, po.senderId, po.senderName,
                po.conversationTitle, SqlTime.toInstant(po.createdAt));
    }

    private FavoritePO toPO(Favorite favorite) {
        FavoritePO po = new FavoritePO();
        po.id = favorite.id();
        po.userId = favorite.userId();
        po.conversationId = favorite.conversationId();
        po.messageId = favorite.messageId();
        po.msgType = favorite.msgType();
        po.content = favorite.content();
        po.fileId = favorite.fileId();
        po.fileName = favorite.fileName();
        po.sizeBytes = favorite.sizeBytes();
        po.contentType = favorite.contentType();
        po.senderId = favorite.senderId();
        po.senderName = favorite.senderName();
        po.conversationTitle = favorite.conversationTitle();
        po.createdAt = SqlTime.toLocal(favorite.createdAt());
        return po;
    }
}
