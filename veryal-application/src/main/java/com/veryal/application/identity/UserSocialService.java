package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.friend.FriendRequest;
import com.veryal.domain.friend.FriendRequestRepository;
import com.veryal.domain.friend.FriendRequestStatus;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserPrivacy;
import com.veryal.domain.identity.UserPrivacyRepository;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.presence.PresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 消费级社交资料、搜索与隐私。
 */
@Service
@RequiredArgsConstructor
public class UserSocialService {

    private static final int MAX_KEYWORD_LENGTH = 32;

    private final UserRepository userRepository;
    private final UserPrivacyRepository privacyRepository;
    private final PresenceRepository presenceRepository;
    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public List<UserSearchView> search(long requesterId, String keyword, int limit) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isEmpty() || value.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "search keyword must be 1-" + MAX_KEYWORD_LENGTH + " characters");
        }
        return userRepository.search(value, limit).stream()
                .filter(user -> user.id() != requesterId)
                .filter(user -> privacy(user.id()).searchByUsername())
                .map(user -> new UserSearchView(user.id(), user.username(), user.nickname(),
                        user.avatar()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileView profile(long requesterId, long targetId) {
        User user = userRepository.findById(targetId)
                .filter(User::active)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserPrivacy privacy = privacy(targetId);
        boolean online = privacy.showOnline() && presenceRepository.isOnline(targetId);
        // 资料卡要区分「好友 / 陌生人 / 自己」三种形态：
        // 好友取 im_friend 的建立时间当「添加时间」，陌生人给发送申请入口。
        Friend friend = requesterId == targetId ? null
                : friendRepository.find(requesterId, targetId).orElse(null);
        boolean pendingOutgoing = false;
        boolean pendingIncoming = false;
        if (requesterId != targetId) {
            pendingOutgoing = pending(requesterId, targetId);
            pendingIncoming = pending(targetId, requesterId);
        }
        return new UserProfileView(user.id(), user.username(), user.nickname(), user.avatar(),
                user.region(), user.signature(), online, user.createdAt(),
                friend != null, friend == null ? null : friend.createdAt(),
                friend == null ? null : friend.remark(), pendingOutgoing, pendingIncoming);
    }

    private boolean pending(long fromUserId, long toUserId) {
        return friendRequestRepository.findBetween(fromUserId, toUserId)
                .filter(request -> request.status() == FriendRequestStatus.PENDING)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public UserPrivacy privacy(long userId) {
        return privacyRepository.findByUserId(userId)
                .orElseGet(() -> UserPrivacy.defaults(userId, timeProvider.now()));
    }

    @Transactional
    public UserPrivacy updatePrivacy(long userId, PrivacyUpdate update) {
        UserPrivacy current = privacy(userId);
        Instant now = timeProvider.now();
        UserPrivacy updated = current.update(
                update.searchByPhone() == null ? current.searchByPhone() : update.searchByPhone(),
                update.searchByUsername() == null ? current.searchByUsername() : update.searchByUsername(),
                update.friendPolicy() == null ? current.friendPolicy() : update.friendPolicy(),
                update.showOnline() == null ? current.showOnline() : update.showOnline(),
                update.readReceipt() == null ? current.readReceipt() : update.readReceipt(),
                update.invitedToGroup() == null ? current.invitedToGroup() : update.invitedToGroup(),
                now);
        privacyRepository.save(updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public UserPrivacy.FriendPolicy friendPolicy(long userId) {
        return privacy(userId).friendPolicy();
    }

    @Transactional(readOnly = true)
    public void ensureCanAddFriend(long targetUserId) {
        if (friendPolicy(targetUserId) == UserPrivacy.FriendPolicy.REJECT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "target rejects friend requests");
        }
    }

    public record UserSearchView(long userId, String username, String nickname, String avatar) {
    }

    public record UserProfileView(long userId, String username, String nickname, String avatar,
                                  String region, String signature, boolean online,
                                  Instant createdAt, boolean friend, Instant friendSince,
                                  String remark, boolean pendingOutgoing,
                                  boolean pendingIncoming) {
    }

    public record PrivacyUpdate(Boolean searchByPhone, Boolean searchByUsername,
                                UserPrivacy.FriendPolicy friendPolicy, Boolean showOnline,
                                Boolean readReceipt, Boolean invitedToGroup) {
    }
}
