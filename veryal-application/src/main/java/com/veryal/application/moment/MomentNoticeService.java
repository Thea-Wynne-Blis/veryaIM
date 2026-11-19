package com.veryal.application.moment;

import com.veryal.common.core.CursorPage;
import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import com.veryal.domain.moment.Moment;
import com.veryal.domain.moment.MomentEvent;
import com.veryal.domain.moment.MomentEventType;
import com.veryal.domain.moment.MomentEvents;
import com.veryal.domain.moment.MomentNotice;
import com.veryal.domain.moment.MomentNoticeRepository;
import com.veryal.domain.moment.MomentNoticeType;
import com.veryal.domain.moment.MomentNotifier;
import com.veryal.domain.moment.MomentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 朋友圈互动通知：落库供未读计数与列表使用，同时走 Outbox + MQ 实时推送。
 */
@Service
@RequiredArgsConstructor
public class MomentNoticeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 60;

    private final MomentNoticeRepository noticeRepository;
    private final MomentRepository momentRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final OutboxAppender outboxAppender;
    private final MomentNotifier momentNotifier;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    /**
     * 记录一条互动并实时推送。接收人自动去重，且永不包含操作者本人。
     */
    public void notify(Collection<Long> recipientIds, long actorId, MomentNoticeType type,
                       long momentId, Long commentId, Instant at) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }
        List<Long> recipients = recipientIds.stream()
                .filter(id -> id != null && id > 0 && id != actorId)
                .distinct()
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        for (Long recipient : recipients) {
            noticeRepository.save(new MomentNotice(idGenerator.nextId(), recipient, actorId, type,
                    momentId, commentId, null, at));
        }
        MomentEvent event = new MomentEvent(RandomIds.newId(), MomentEventType.from(type), momentId,
                actorId, commentId, recipients, at);
        outboxAppender.append(new OutboxMessage(event.eventId(), "moment:" + momentId,
                MomentEvents.NOTIFY, JsonUtils.toJsonBytes(event), at));
        momentNotifier.notify(event);
    }

    /** 动态被删除：清掉它的互动通知，未读数随之减少。 */
    public void discardMoment(long momentId) {
        noticeRepository.deleteByMoment(momentId);
    }

    /** 评论被删除：清掉指向该评论的通知。 */
    public void discardComment(long commentId) {
        noticeRepository.deleteByComment(commentId);
    }

    @Transactional(readOnly = true)
    public CursorPage<MomentNoticeView> list(long userId, String cursor, int limit) {        int size = pageSize(limit);
        long cursorId = parseCursor(cursor);
        List<MomentNotice> rows = new ArrayList<>(noticeRepository.list(userId, cursorId, size + 1));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        Set<Long> actorIds = rows.stream().map(MomentNotice::actorId).collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findByIds(actorIds).stream()
                .collect(Collectors.toMap(User::id, user -> user));
        Map<Long, String> remarks = friendRepository.remarks(userId, actorIds);
        Map<Long, String> previews = momentRepository
                .findByIds(rows.stream().map(MomentNotice::momentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Moment::id, moment -> preview(moment.content())));

        List<MomentNoticeView> items = rows.stream().map(notice -> {
            User actor = users.get(notice.actorId());
            MomentAuthorView actorView = new MomentAuthorView(notice.actorId(),
                    actor == null ? "" : actor.nickname(),
                    actor == null ? null : actor.avatar(), remarks.get(notice.actorId()));
            return new MomentNoticeView(notice.id(), notice.type().name(), actorView,
                    notice.momentId(), notice.commentId(),
                    previews.getOrDefault(notice.momentId(), ""),
                    notice.readAt() != null, notice.createdAt());
        }).toList();

        String nextCursor = hasMore ? Long.toString(rows.get(rows.size() - 1).id()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    public long unreadCount(long userId) {
        return noticeRepository.unreadCount(userId);
    }

    @Transactional
    public void markAllRead(long userId) {
        noticeRepository.markAllRead(userId, timeProvider.now());
    }

    private String preview(String content) {
        String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return text.length() <= PREVIEW_LENGTH ? text
                : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private int pageSize(int limit) {
        return limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            long value = Long.parseLong(cursor.trim());
            return value <= 0 ? Long.MAX_VALUE : value;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
