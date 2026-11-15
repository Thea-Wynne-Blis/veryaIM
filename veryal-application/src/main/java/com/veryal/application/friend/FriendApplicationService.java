package com.veryal.application.friend;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.friend.BlacklistRepository;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.friend.FriendRequest;
import com.veryal.domain.friend.FriendRequestRepository;
import com.veryal.domain.friend.FriendRequestStatus;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.presence.PresenceRepository;
import com.veryal.domain.presence.PresenceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
    private final PresenceRepository presenceRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public FriendRequestView sendRequest(long tenantId, long fromUserId, long toUserId, String message) {
        if (fromUserId == toUserId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot add yourself as friend");
        }
        User target = requireUser(tenantId, toUserId);
        target.ensureActive();
        if (friendRepository.find(tenantId, fromUserId, toUserId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_FRIEND);
        }
        if (blacklistRepository.exists(tenantId, toUserId, fromUserId)
                || blacklistRepository.exists(tenantId, fromUserId, toUserId)) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
        String text = message == null ? null : message.trim();
        if (text != null && text.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "request message is too long");
        }
        Instant now = timeProvider.now();

        FriendRequest reverse = friendRequestRepository.findBetween(tenantId, toUserId, fromUserId)
                .filter(item -> item.status() == FriendRequestStatus.PENDING).orElse(null);
        if (reverse != null) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_EXISTS,
                    "reverse pending request exists, accept it instead");
        }

        FriendRequest existing = friendRequestRepository.findBetween(tenantId, fromUserId, toUserId)
                .orElse(null);
        if (existing != null && existing.status() == FriendRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_EXISTS);
        }
        FriendRequest request;
        if (existing == null) {
            request = new FriendRequest(idGenerator.nextId(), tenantId, fromUserId, toUserId, text,
                    FriendRequestStatus.PENDING, now, now);
            friendRequestRepository.save(request);
        } else {
            request = new FriendRequest(existing.id(), tenantId, fromUserId, toUserId, text,
                    FriendRequestStatus.PENDING, existing.createdAt(), now);
            friendRequestRepository.update(request);
        }
        return view(tenantId, request);
    }

    @Transactional
    public FriendRequestView accept(long tenantId, long userId, long requestId) {
        FriendRequest request = requireRequest(tenantId, userId, requestId, true);
        Instant now = timeProvider.now();
        addFriend(tenantId, request.fromUserId(), request.toUserId(), now);
        addFriend(tenantId, request.toUserId(), request.fromUserId(), now);
        FriendRequest accepted = new FriendRequest(request.id(), tenantId, request.fromUserId(),
                request.toUserId(), request.message(), FriendRequestStatus.ACCEPTED,
                request.createdAt(), now);
        friendRequestRepository.update(accepted);
        return view(tenantId, accepted);
    }

    @Transactional
    public FriendRequestView reject(long tenantId, long userId, long requestId) {
        FriendRequest request = requireRequest(tenantId, userId, requestId, true);
        FriendRequest rejected = new FriendRequest(request.id(), tenantId, request.fromUserId(),
                request.toUserId(), request.message(), FriendRequestStatus.REJECTED,
                request.createdAt(), timeProvider.now());
        friendRequestRepository.update(rejected);
        return view(tenantId, rejected);
    }

    @Transactional
    public FriendRequestView cancel(long tenantId, long userId, long requestId) {
        FriendRequest request = requireRequest(tenantId, userId, requestId, false);
        FriendRequest canceled = new FriendRequest(request.id(), tenantId, request.fromUserId(),
                request.toUserId(), request.message(), FriendRequestStatus.CANCELED,
                request.createdAt(), timeProvider.now());
        friendRequestRepository.update(canceled);
        return view(tenantId, canceled);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestView> incoming(long tenantId, long userId, int limit) {
        return friendRequestRepository.listIncoming(tenantId, userId, limit(limit)).stream()
                .map(item -> view(tenantId, item))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestView> outgoing(long tenantId, long userId, int limit) {
        return friendRequestRepository.listOutgoing(tenantId, userId, limit(limit)).stream()
                .map(item -> view(tenantId, item))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendView> listFriends(long tenantId, long userId) {
        return friendRepository.listByUser(tenantId, userId, MAX_LIST_SIZE).stream()
                .map(friend -> {
                    User user = userRepository.findById(friend.friendId()).orElse(null);
                    String nickname = user == null ? "" : user.nickname();
                    String avatar = user == null ? null : user.avatar();
                    return new FriendView(friend.friendId(), nickname, avatar, friend.remark(),
                            online(friend.friendId()), friend.createdAt());
                })
                .toList();
    }

    @Transactional
    public void updateRemark(long tenantId, long userId, long friendId, String remark) {
        friendRepository.find(tenantId, userId, friendId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND,
                        "friend not found"));
        String value = remark == null ? null : remark.trim();
        if (value != null && value.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "remark is too long");
        }
        friendRepository.updateRemark(tenantId, userId, friendId, value);
    }

    @Transactional
    public void deleteFriend(long tenantId, long userId, long friendId) {
        friendRepository.delete(tenantId, userId, friendId);
        friendRepository.delete(tenantId, friendId, userId);
    }

    @Transactional
    public void block(long tenantId, long userId, long blockedUserId) {
        if (userId == blockedUserId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot block yourself");
        }
        requireUser(tenantId, blockedUserId);
        if (!blacklistRepository.exists(tenantId, userId, blockedUserId)) {
            blacklistRepository.save(tenantId, userId, blockedUserId);
        }
        friendRepository.delete(tenantId, userId, blockedUserId);
        friendRepository.delete(tenantId, blockedUserId, userId);
        cancelPending(tenantId, userId, blockedUserId);
        cancelPending(tenantId, blockedUserId, userId);
    }

    @Transactional
    public void unblock(long tenantId, long userId, long blockedUserId) {
        blacklistRepository.delete(tenantId, userId, blockedUserId);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserView> listBlocked(long tenantId, long userId) {
        return blacklistRepository.list(tenantId, userId, MAX_LIST_SIZE).stream()
                .map(id -> {
                    User user = userRepository.findById(id).orElse(null);
                    return new BlockedUserView(id, user == null ? "" : user.nickname(),
                            user == null ? null : user.avatar());
                })
                .toList();
    }

    /**
     * 私聊发送校验：接收者把发送者拉黑时拒绝。
     */
    public boolean blocked(long tenantId, long userId, long otherUserId) {
        return blacklistRepository.exists(tenantId, otherUserId, userId);
    }

    public long pendingIncoming(long tenantId, long userId) {
        return friendRequestRepository.countPendingIncoming(tenantId, userId);
    }

    private void addFriend(long tenantId, long userId, long friendId, Instant now) {
        if (friendRepository.find(tenantId, userId, friendId).isPresent()) {
            return;
        }
        friendRepository.save(new Friend(idGenerator.nextId(), tenantId, userId, friendId, null, now));
    }

    private void cancelPending(long tenantId, long fromUserId, long toUserId) {
        friendRequestRepository.findBetween(tenantId, fromUserId, toUserId)
                .filter(item -> item.status() == FriendRequestStatus.PENDING)
                .ifPresent(item -> friendRequestRepository.update(new FriendRequest(item.id(), tenantId,
                        item.fromUserId(), item.toUserId(), item.message(),
                        FriendRequestStatus.CANCELED, item.createdAt(), timeProvider.now())));
    }

    private FriendRequest requireRequest(long tenantId, long userId, long requestId, boolean incoming) {
        FriendRequest request = friendRequestRepository.findById(tenantId, requestId)
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

    private FriendRequestView view(long tenantId, FriendRequest request) {
        String fromNickname = userRepository.findById(request.fromUserId())
                .map(User::nickname).orElse("");
        String toNickname = userRepository.findById(request.toUserId())
                .map(User::nickname).orElse("");
        return FriendRequestView.of(request, fromNickname, toNickname);
    }

    private User requireUser(long tenantId, long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.tenantId() == tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean online(long userId) {
        return presenceRepository.findByUserId(userId).stream()
                .anyMatch(item -> item.status() == PresenceStatus.ONLINE);
    }

    private int limit(int limit) {
        return limit <= 0 ? 50 : Math.min(limit, MAX_LIST_SIZE);
    }
}
