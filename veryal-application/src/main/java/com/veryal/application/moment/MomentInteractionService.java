package com.veryal.application.moment;

import com.veryal.application.moderation.SensitiveWordService;
import com.veryal.common.core.CursorPage;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.moment.Moment;
import com.veryal.domain.moment.MomentComment;
import com.veryal.domain.moment.MomentInteractionRepository;
import com.veryal.domain.moment.MomentNoticeType;
import com.veryal.domain.moment.MomentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 朋友圈互动：点赞与评论。
 *
 * <p>点赞与评论计数在动态行上原子增减，读信息流不执行 COUNT(*)；
 * 通知只发给动态作者与被回复的人，且永远不发给自己。</p>
 */
@Service
@RequiredArgsConstructor
public class MomentInteractionService {

    private static final int DEFAULT_COMMENT_PAGE = 20;
    private static final int MAX_COMMENT_PAGE = 50;

    private final MomentRepository momentRepository;
    private final MomentInteractionRepository interactionRepository;
    private final MomentAccessPolicy accessPolicy;
    private final MomentViewAssembler assembler;
    private final MomentNoticeService noticeService;
    private final SensitiveWordService sensitiveWordService;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public MomentView like(long userId, long momentId, boolean liked) {
        Moment moment = accessPolicy.requireViewable(userId, momentId);
        Instant now = timeProvider.now();
        boolean changed = liked
                ? interactionRepository.addLike(momentId, userId, now)
                : interactionRepository.removeLike(momentId, userId);
        if (changed) {
            interactionRepository.addLikeCount(momentId, liked ? 1 : -1);
            if (liked && !moment.ownedBy(userId)) {
                noticeService.notify(List.of(moment.authorId()), userId,
                        MomentNoticeType.LIKE, momentId, null, now);
            }
        }
        return assembler.assembleOne(userId,
                momentRepository.findById(momentId).orElse(moment));
    }

    @Transactional
    public MomentCommentView comment(long userId, long momentId, String content,
                                     Long replyToCommentId) {
        Moment moment = accessPolicy.requireViewable(userId, momentId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "comment must not be empty");
        }
        if (text.length() > MomentComment.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "comment exceeds " + MomentComment.MAX_CONTENT_LENGTH + " characters");
        }
        sensitiveWordService.checkText(text);

        Instant now = timeProvider.now();
        Long replyToId = null;
        Long replyToUserId = null;
        if (replyToCommentId != null) {
            MomentComment parent = interactionRepository.findComment(replyToCommentId)
                    .filter(item -> item.momentId() == momentId && !item.deleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MOMENT_COMMENT_NOT_FOUND));
            replyToId = parent.id();
            replyToUserId = parent.userId();
        }
        MomentComment comment = new MomentComment(idGenerator.nextId(), momentId, userId,
                replyToId, replyToUserId, text, now, null);
        interactionRepository.saveComment(comment);
        interactionRepository.addCommentCount(momentId, 1);

        Set<Long> recipients = new LinkedHashSet<>();
        if (!moment.ownedBy(userId)) {
            recipients.add(moment.authorId());
        }
        if (replyToUserId != null) {
            recipients.add(replyToUserId);
        }
        noticeService.notify(recipients, userId,
                replyToUserId == null ? MomentNoticeType.COMMENT : MomentNoticeType.REPLY,
                momentId, comment.id(), now);
        return assembler.assembleComment(userId, comment);
    }

    @Transactional
    public void deleteComment(long userId, long commentId) {
        MomentComment comment = interactionRepository.findComment(commentId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOMENT_COMMENT_NOT_FOUND));
        Moment moment = momentRepository.findById(comment.momentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOMENT_NOT_FOUND));
        if (comment.userId() != userId && !moment.ownedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot delete this comment");
        }
        interactionRepository.softDeleteComment(commentId, timeProvider.now());
        interactionRepository.addCommentCount(comment.momentId(), -1);
        noticeService.discardComment(commentId);
    }

    @Transactional(readOnly = true)
    public CursorPage<MomentCommentView> comments(long userId, long momentId, String cursor,
                                                  int limit) {
        accessPolicy.requireViewable(userId, momentId);
        int size = limit <= 0 ? DEFAULT_COMMENT_PAGE : Math.min(limit, MAX_COMMENT_PAGE);
        long cursorId = parseCursor(cursor);
        List<MomentComment> rows = new ArrayList<>(interactionRepository.listComments(
                momentId, cursorId, size + 1));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        List<MomentCommentView> items = assembler.assembleComments(userId, rows);
        String nextCursor = hasMore ? Long.toString(rows.get(rows.size() - 1).id()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(cursor.trim()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid cursor");
        }
    }
}
