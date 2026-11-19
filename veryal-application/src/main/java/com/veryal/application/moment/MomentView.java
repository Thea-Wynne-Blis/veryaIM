package com.veryal.application.moment;

import java.time.Instant;
import java.util.List;

/**
 * 朋友圈动态视图。
 *
 * <p>点赞人与评论只带前若干条预览，完整评论走评论分页接口；
 * 计数取自动态行上的冗余字段，避免读路径 COUNT(*)。</p>
 */
public record MomentView(long id, MomentAuthorView author, String content, String visibility,
                         List<MomentMediaView> media, int likeCount, int commentCount,
                         List<MomentAuthorView> likers, List<MomentCommentView> comments,
                         boolean liked, boolean mine, boolean hidden, Instant createdAt) {
}
