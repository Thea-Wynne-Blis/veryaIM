package com.veryal.domain.moment;

import java.time.Instant;

/**
 * 一条朋友圈动态。
 *
 * <p>可见性、时间范围与好友关系都不在聚合内部判断：它们依赖好友表与作者设置，
 * 由应用层的访问策略统一裁决，保证信息流、详情、媒体、评论使用同一套规则。</p>
 */
public record Moment(long id, long authorId, String content, MomentVisibility visibility,
                     int likeCount, int commentCount, MomentStatus status,
                     Instant createdAt, Instant deletedAt) {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_MEDIA_COUNT = 9;

    public Moment {
        if (id <= 0 || authorId <= 0) {
            throw new IllegalArgumentException("moment ids must be positive");
        }
        if (content == null) {
            content = "";
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        if (visibility == null) {
            visibility = MomentVisibility.ALL_FRIENDS;
        }
        if (status == null) {
            status = MomentStatus.NORMAL;
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (likeCount < 0) {
            likeCount = 0;
        }
        if (commentCount < 0) {
            commentCount = 0;
        }
    }

    public boolean deleted() {
        return deletedAt != null;
    }

    public boolean hidden() {
        return status == MomentStatus.HIDDEN;
    }

    public boolean ownedBy(long userId) {
        return authorId == userId;
    }

    public Moment withStatus(MomentStatus newStatus) {
        return new Moment(id, authorId, content, visibility, likeCount, commentCount,
                newStatus, createdAt, deletedAt);
    }
}
