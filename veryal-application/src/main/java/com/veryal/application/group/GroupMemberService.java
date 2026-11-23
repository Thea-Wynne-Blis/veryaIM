package com.veryal.application.group;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.group.GroupModerationRepository;
import com.veryal.domain.group.GroupMute;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserPrivacy;
import com.veryal.domain.identity.UserPrivacyRepository;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.presence.PresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 群成员目录：一次返回昵称、角色、在线与禁言状态。
 *
 * <p>此前前端拿到 memberIds 后逐个请求 {@code /users/{id}/profile}，一个 50 人群就是 50 次
 * HTTP + 50 次数据库查询，打开群详情会明显卡顿。这里改成「成员 + 用户资料 + 隐私 + 禁言」
 * 三次批量查询，排序固定为群主、管理员、成员。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupRepository groupRepository;
    private final GroupModerationRepository moderationRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final UserRepository userRepository;
    private final UserPrivacyRepository privacyRepository;
    private final PresenceRepository presenceRepository;
    private final FriendRepository friendRepository;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public List<GroupMemberView> list(long viewerId, String groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "group not found"));
        groupAccessGuard.requireMember(groupId, viewerId);

        List<GroupMember> members = groupRepository.findMembers(group.id());
        List<Long> userIds = members.stream().map(GroupMember::userId).toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        Map<Long, Boolean> showOnline = privacyRepository.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(UserPrivacy::userId, UserPrivacy::showOnline));
        Instant now = timeProvider.now();
        Set<Long> onlineIds = presenceRepository.onlineUserIds(userIds);
        Map<Long, GroupMute> mutes = moderationRepository.findMutes(group.id()).stream()
                .filter(mute -> mute.activeAt(now))
                .collect(Collectors.toMap(GroupMute::userId, Function.identity()));
        // 好友备注优先：群成员列表和消息作者名都该跟我通讯录里看到的一致。
        Map<Long, String> remarks = friendRepository.listByUser(viewerId, 500).stream()
                .filter(friend -> friend.remark() != null && !friend.remark().isBlank())
                .collect(Collectors.toMap(Friend::friendId, Friend::remark, (a, b) -> a));

        return members.stream()
                .sorted(Comparator.comparingInt((GroupMember member) -> -member.role().code())
                        .thenComparingLong(GroupMember::userId))
                .map(member -> {
                    User user = users.get(member.userId());
                    GroupMute mute = mutes.get(member.userId());
                    boolean online = showOnline.getOrDefault(member.userId(), true)
                            && onlineIds.contains(member.userId());
                    return new GroupMemberView(member.userId(),
                            user == null ? "用户 " + member.userId() : user.nickname(),
                            remarks.get(member.userId()),
                            user == null ? null : user.avatar(),
                            online, member.role().name(),
                            mute == null ? null : mute.mutedUntil(),
                            mute != null && mute.permanent());
                })
                .toList();
    }
}
