package com.veryal.application.moment;

import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.moment.Moment;
import com.veryal.domain.moment.MomentComment;
import com.veryal.domain.moment.MomentInteractionRepository;
import com.veryal.domain.moment.MomentLike;
import com.veryal.domain.moment.MomentMedia;
import com.veryal.domain.moment.MomentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 朋友圈视图装配。
 *
 * <p>一页动态固定执行：媒体 1 次、点赞 1 次、评论预览 1 次、当前用户点赞状态 1 次、
 * 用户资料 1 次、好友备注 1 次。禁止在循环里查库。</p>
 */
@Component
@RequiredArgsConstructor
public class MomentViewAssembler {

    private static final int LIKER_PREVIEW = 8;
    private static final int COMMENT_PREVIEW = 5;

    private final MomentRepository momentRepository;
    private final MomentInteractionRepository interactionRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;

    public List<MomentView> assemble(long viewerId, List<Moment> moments) {
        if (moments.isEmpty()) {
            return List.of();
        }
        List<Long> momentIds = moments.stream().map(Moment::id).toList();

        Map<Long, List<MomentMedia>> mediaByMoment = momentRepository.listMedia(momentIds).stream()
                .collect(Collectors.groupingBy(MomentMedia::momentId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, List<MomentLike>> likersByMoment = interactionRepository
                .listLikers(momentIds, LIKER_PREVIEW).stream()
                .collect(Collectors.groupingBy(MomentLike::momentId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, List<MomentComment>> commentsByMoment = interactionRepository
                .listFirstComments(momentIds, COMMENT_PREVIEW).stream()
                .collect(Collectors.groupingBy(MomentComment::momentId, LinkedHashMap::new,
                        Collectors.toList()));
        Set<Long> likedIds = interactionRepository.likedMomentIds(viewerId, momentIds);

        Set<Long> userIds = new HashSet<>();
        moments.forEach(moment -> userIds.add(moment.authorId()));
        likersByMoment.values().forEach(list -> list.forEach(like -> userIds.add(like.userId())));
        commentsByMoment.values().forEach(list -> list.forEach(comment -> {
            userIds.add(comment.userId());
            if (comment.replyToUserId() != null) {
                userIds.add(comment.replyToUserId());
            }
        }));
        Map<Long, User> users = loadUsers(userIds);
        Map<Long, String> remarks = friendRepository.remarks(viewerId, userIds);

        return moments.stream().map(moment -> new MomentView(
                moment.id(),
                author(moment.authorId(), users, remarks),
                moment.content(),
                moment.visibility().name(),
                mediaByMoment.getOrDefault(moment.id(), List.of()).stream()
                        .map(this::mediaView).toList(),
                moment.likeCount(),
                moment.commentCount(),
                likersByMoment.getOrDefault(moment.id(), List.of()).stream()
                        .map(like -> author(like.userId(), users, remarks)).toList(),
                commentsByMoment.getOrDefault(moment.id(), List.of()).stream()
                        .map(comment -> commentView(viewerId, comment, users, remarks)).toList(),
                likedIds.contains(moment.id()),
                moment.ownedBy(viewerId),
                moment.hidden(),
                moment.createdAt())).toList();
    }

    public MomentView assembleOne(long viewerId, Moment moment) {
        return assemble(viewerId, List.of(moment)).get(0);
    }

    public MomentCommentView assembleComment(long viewerId, MomentComment comment) {
        return assembleComments(viewerId, List.of(comment)).get(0);
    }

    public List<MomentCommentView> assembleComments(long viewerId, List<MomentComment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        comments.forEach(comment -> {
            userIds.add(comment.userId());
            if (comment.replyToUserId() != null) {
                userIds.add(comment.replyToUserId());
            }
        });
        Map<Long, User> users = loadUsers(userIds);
        Map<Long, String> remarks = friendRepository.remarks(viewerId, userIds);
        return comments.stream()
                .map(comment -> commentView(viewerId, comment, users, remarks))
                .toList();
    }

    private MomentCommentView commentView(long viewerId, MomentComment comment,
                                          Map<Long, User> users, Map<Long, String> remarks) {
        MomentAuthorView author = author(comment.userId(), users, remarks);
        MomentAuthorView replyTo = comment.replyToUserId() == null ? null
                : author(comment.replyToUserId(), users, remarks);
        return new MomentCommentView(comment.id(), author, comment.content(), comment.replyToId(),
                comment.replyToUserId(), replyTo == null ? null : displayName(replyTo),
                comment.createdAt(), comment.userId() == viewerId);
    }

    private MomentMediaView mediaView(MomentMedia media) {
        return new MomentMediaView(media.fileId(), media.kind().name(), media.sortOrder(),
                media.width(), media.height(), media.durationSeconds());
    }

    private MomentAuthorView author(long userId, Map<Long, User> users, Map<Long, String> remarks) {
        User user = users.get(userId);
        return new MomentAuthorView(userId, user == null ? "" : user.nickname(),
                user == null ? null : user.avatar(), remarks.get(userId));
    }

    private String displayName(MomentAuthorView author) {
        return author.remark() == null || author.remark().isBlank()
                ? author.nickname() : author.remark();
    }

    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::id, user -> user));
    }
}
