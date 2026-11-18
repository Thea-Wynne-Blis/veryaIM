package com.veryal.application.friend;

import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.application.identity.UserSocialService;
import com.veryal.domain.friend.BlacklistRepository;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.friend.FriendEventType;
import com.veryal.domain.friend.FriendEvents;
import com.veryal.domain.friend.FriendNotifier;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.friend.FriendRequest;
import com.veryal.domain.friend.FriendRequestRepository;
import com.veryal.domain.friend.FriendRequestStatus;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.outbox.OutboxAppender;
import com.veryal.domain.message.outbox.OutboxMessage;
import com.veryal.domain.presence.PresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 好友与黑名单应用服务。
 */
@Service
@RequiredArgsConstructor
public class FriendApplicationService {

    private static final int MAX_LIST_SIZE = 200;
    private static final int MAX_MESSAGE_LENGTH = 255;

    private final FriendRequestRepository friendRequestRepository;
    private final FriendRepository friendRepository;
    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final UserSocialService userSocialService;
    private final PresenceRepository presenceRepository;
    private final OutboxAppender outboxAppender;
    private final FriendNotifier friendNotifier;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public FriendRequestView sendRequest(long fromUserId, long toUserId, String message) {
        if (fromUserId == toUserId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot add yourself as friend");
        }
        User target = requireUser(toUserId);
        target.ensureActive();
        if (friendRepository.find(fromUserId, toUserId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_FRIEND);
        }
        if (blacklistRepository.exists(toUserId, fromUserId)
                || blacklistRepository.exists(fromUserId, toUserId)) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
        userSocialService.ensureCanAddFriend(toUserId);
        com.veryal.domain.identity.UserPrivacy.FriendPolicy policy =
                userSocialService.friendPolicy(toUserId);
        String text = message == null ? null : message.trim();
        if (text != null && text.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "request message is too long");
        }
        Instant now = timeProvider.now();

        if (policy == com.veryal.domain.identity.UserPrivacy.FriendPolicy.DIRECT) {
            cancelPending(toUserId, fromUserId);
            addFriend(fromUserId, toUserId, now);
            addFriend(toUserId, fromUserId, now);
            FriendRequest accepted = new FriendRequest(idGenerator.nextId(), fromUserId,
                    toUserId, text, FriendRequestStatus.ACCEPTED, now, now);
            friendRequestRepository.save(accepted);
            publish(FriendEventType.ACCEPTED, accepted, text, now);
            return view(accepted);
        }

        FriendRequest reverse = friendRequestRepository.findBetween(toUserId, fromUserId)
                .filter(item -> item.status() == FriendRequestStatus.PENDING).orElse(null);
        if (reverse != null) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_EXISTS,
                    "reverse pending request exists, accept it instead");
        }

        FriendRequest existing = friendRequestRepository.findBetween(fromUserId, toUserId)
                .orElse(null);
        if (existing != null && existing.status() == FriendRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_EXISTS);
        }
        FriendRequest request;
        if (existing == null) {
            request = new FriendRequest(idGenerator.nextId(), fromUserId, toUserId, text,
                    FriendRequestStatus.PENDING, now, now);
            friendRequestRepository.save(request);
        } else {
            request = new FriendRequest(existing.id(), fromUserId, toUserId, text,
                    FriendRequestStatus.PENDING, existing.createdAt(), now);
            friendRequestRepository.update(request);
        }
        publish(FriendEventType.REQUEST, request, text, now);
        return view(request);
    }

    @Transactional
    public FriendRequestView accept(long userId, long requestId) {
        FriendRequest request = requireRequest(userId, requestId, true);
        Instant now = timeProvider.now();
        addFriend(request.fromUserId(), request.toUserId(), now);
        addFriend(request.toUserId(), request.fromUserId(), now);
        FriendRequest accepted = new FriendRequest(request.id(), request.fromUserId(),
                request.toUserId(), request.message(), FriendRequestStatus.ACCEPTED,
                request.createdAt(), now);
        friendRequestRepository.update(accepted);
        publish(FriendEventType.ACCEPTED, accepted, accepted.message(), now);
        return view(accepted);
    }

    @Transactional
    public FriendRequestView reject(long userId, long requestId) {
        FriendRequest request = requireRequest(userId, requestId, true);
        FriendRequest rejected = new FriendRequest(request.id(), request.fromUserId(),
                request.toUserId(), request.message(), FriendRequestStatus.REJECTED,
                request.createdAt(), timeProvider.now());
        friendRequestRepository.update(rejected);
        publish(FriendEventType.REJECTED, rejected, rejected.message(), rejected.updatedAt());
        return view(rejected);
    }

    @Transactional
    public FriendRequestView cancel(long userId, long requestId) {
        FriendRequest request = requireRequest(userId, requestId, false);
        FriendRequest canceled = new FriendRequest(request.id(), request.fromUserId(),
                request.toUserId(), request.message(), FriendRequestStatus.CANCELED,
                request.createdAt(), timeProvider.now());
        friendRequestRepository.update(canceled);
        publish(FriendEventType.CANCELED, canceled, canceled.message(), canceled.updatedAt());
        return view(canceled);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestView> incoming(long userId, int limit) {
        return friendRequestRepository.listIncoming(userId, limit(limit)).stream()
                .map(item -> view(item))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestView> outgoing(long userId, int limit) {
        return friendRequestRepository.listOutgoing(userId, limit(limit)).stream()
                .map(item -> view(item))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendView> listFriends(long userId) {
        List<Friend> relations = friendRepository.listByUser(userId, MAX_LIST_SIZE);
        if (relations.isEmpty()) {
            return List.of();
        }
        // 好友列表一次批量取用户资料与在线状态：通讯录是首屏接口，
        // 之前每个好友各查一次用户表 + 一次 Redis，好友多了会明显拖慢。
        Map<Long, User> users = userRepository.findByIds(
                        relations.stream().map(Friend::friendId).toList())
                .stream()
                .collect(Collectors.toMap(User::id, user -> user));
        Set<Long> onlineIds = presenceRepository.onlineUserIds(
                relations.stream().map(Friend::friendId).toList());
        return relations.stream()
                .map(friend -> {
                    User user = users.get(friend.friendId());
                    return new FriendView(friend.friendId(),
                            user == null ? "" : user.nickname(),
                            user == null ? null : user.avatar(),
                            user == null ? null : user.region(),
                            user == null ? null : user.signature(),
                            friend.remark(), onlineIds.contains(friend.friendId()),
                            friend.createdAt());
                })
                .toList();
    }

    @Transactional
    public void updateRemark(long userId, long friendId, String remark) {
        friendRepository.find(userId, friendId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND,
                        "friend not found"));
        String value = remark == null ? null : remark.trim();
        if (value != null && value.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "remark is too long");
        }
        friendRepository.updateRemark(userId, friendId, value);
    }

    @Transactional
    public void deleteFriend(long userId, long friendId) {
        friendRepository.delete(userId, friendId);
        friendRepository.delete(friendId, userId);
    }

    @Transactional
    public void block(long userId, long blockedUserId) {
        if (userId == blockedUserId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot block yourself");
        }
        requireUser(blockedUserId);
        if (!blacklistRepository.exists(userId, blockedUserId)) {
            blacklistRepository.save(userId, blockedUserId);
        }
        friendRepository.delete(userId, blockedUserId);
        friendRepository.delete(blockedUserId, userId);
        cancelPending(userId, blockedUserId);
        cancelPending(blockedUserId, userId);
        publishRaw(FriendEventType.BLOCKED, 0L, userId, blockedUserId, null,
                timeProvider.now());
    }

    @Transactional
    public void unblock(long userId, long blockedUserId) {
        blacklistRepository.delete(userId, blockedUserId);
        publishRaw(FriendEventType.UNBLOCKED, 0L, userId, blockedUserId, null,
                timeProvider.now());
    }

    @Transactional(readOnly = true)
    public List<BlockedUserView> listBlocked(long userId) {
        return blacklistRepository.list(userId, MAX_LIST_SIZE).stream()
                .map(id -> {
                    User user = userRepository.findById(id).orElse(null);
                    return new BlockedUserView(id, user == null ? "" : user.nickname(),
                            user == null ? null : user.avatar());
                })
                .toList();
    }

    public long pendingIncoming(long userId) {
        return friendRequestRepository.countPendingIncoming(userId);
    }

    private void publish(FriendEventType type, FriendRequest request, String message,
                         Instant at) {
        publishRaw(type, request.id(), request.fromUserId(), request.toUserId(),
                message, at);
    }

    private void publishRaw(FriendEventType type, long requestId, long fromUserId,
                            long toUserId, String message, Instant at) {
        FriendEvent event = new FriendEvent(RandomIds.newId(), type, requestId,
                fromUserId, toUserId, message, at, List.of(fromUserId, toUserId));
        outboxAppender.append(new OutboxMessage(event.eventId(), "friend:" + requestId,
                FriendEvents.NOTIFY, JsonUtils.toJsonBytes(event), at));
        friendNotifier.notify(event);
    }

    private void addFriend(long userId, long friendId, Instant now) {        if (friendRepository.find(userId, friendId).isPresent()) {
            return;
        }
        friendRepository.save(new Friend(idGenerator.nextId(), userId, friendId, null, now));
    }

    private void cancelPending(long fromUserId, long toUserId) {
        friendRequestRepository.findBetween(fromUserId, toUserId)
                .filter(item -> item.status() == FriendRequestStatus.PENDING)
                .ifPresent(item -> friendRequestRepository.update(new FriendRequest(item.id(),
                        item.fromUserId(), item.toUserId(), item.message(),
                        FriendRequestStatus.CANCELED, item.createdAt(), timeProvider.now())));
    }

    private FriendRequest requireRequest(long userId, long requestId, boolean incoming) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        long owner = incoming ? request.toUserId() : request.fromUserId();
        if (owner != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "request does not belong to user");
        }
        if (request.status() != FriendRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "request is not pending");
        }
        return request;
    }

    private FriendRequestView view(FriendRequest request) {
        String fromNickname = userRepository.findById(request.fromUserId())
                .map(User::nickname).orElse("");
        String toNickname = userRepository.findById(request.toUserId())
                .map(User::nickname).orElse("");
        return FriendRequestView.of(request, fromNickname, toNickname);
    }

    private User requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private int limit(int limit) {
        return limit <= 0 ? 50 : Math.min(limit, MAX_LIST_SIZE);
    }
}
