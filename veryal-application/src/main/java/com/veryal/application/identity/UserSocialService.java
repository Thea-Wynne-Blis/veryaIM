package com.veryal.application.identity;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
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
        return new UserProfileView(user.id(), user.username(), user.nickname(), user.avatar(),
                online, user.createdAt());
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
                                  boolean online, Instant createdAt) {
    }

    public record PrivacyUpdate(Boolean searchByPhone, Boolean searchByUsername,
                                UserPrivacy.FriendPolicy friendPolicy, Boolean showOnline,
                                Boolean readReceipt, Boolean invitedToGroup) {
    }
}
