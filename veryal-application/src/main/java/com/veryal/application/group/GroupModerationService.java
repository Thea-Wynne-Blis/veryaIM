package com.veryal.application.group;

import com.veryal.application.message.SystemMessageService;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.group.GroupModerationRepository;
import com.veryal.domain.group.GroupMute;
import com.veryal.domain.group.GroupMuteDuration;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.group.GroupRole;
import com.veryal.domain.group.GroupSetting;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 群公告、全员禁言、成员禁言与 @ 校验。
 *
 * <p>权限按角色区分：改群名/公告是群主与管理员，禁言（单人、全员）只有群主。
 * 控制器上的 {@code @RequireGroupRole} 做入口拦截，这里再校验一次具体目标角色，
 * 避免绕过入口直接调用服务。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupModerationService {

    private static final int MAX_ANNOUNCEMENT = 2000;
    private static final int MAX_MENTIONS = 20;

    private final GroupModerationRepository moderationRepository;
    private final GroupRepository groupRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final UserRepository userRepository;
    private final SystemMessageService systemMessageService;
    private final TimeProvider timeProvider;

    public ModerationView get(long userId, String groupId) {
        requireGroup(groupId);
        requireMember(groupId, userId);
        GroupSetting setting = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        return view(groupId, setting, moderationRepository.findMute(groupId, userId).orElse(null));
    }

    @Transactional
    public ModerationView updateAnnouncement(long operatorId, String groupId,
                                             String announcement) {
        requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.ADMIN);
        String value = announcement == null ? "" : announcement.trim();
        if (value.length() > MAX_ANNOUNCEMENT) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "announcement too long (max " + MAX_ANNOUNCEMENT + ")");
        }
        GroupSetting current = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        GroupSetting updated = new GroupSetting(groupId, value.isEmpty() ? null : value,
                current.muteAll(), timeProvider.now(), operatorId);
        moderationRepository.saveSetting(updated);
        return view(groupId, updated, moderationRepository.findMute(groupId, operatorId).orElse(null));
    }

    @Transactional
    public ModerationView setMuteAll(long operatorId, String groupId,
                                     boolean muteAll) {
        requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.OWNER);
        GroupSetting current = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        GroupSetting updated = new GroupSetting(groupId, current.announcement(), muteAll,
                timeProvider.now(), operatorId);
        moderationRepository.saveSetting(updated);
        // 全员禁言状态变化也留一条聊天框提示，成员不用去群管理里才发现不能发言。
        if (current.muteAll() != muteAll) {
            systemMessageService.appendGroupNotice(groupId, operatorId,
                    nicknameOf(operatorId) + (muteAll ? " 开启了全员禁言" : " 关闭了全员禁言"));
        }
        return view(groupId, updated, moderationRepository.findMute(groupId, operatorId).orElse(null));
    }

    /**
     * 禁言/解除禁言单个成员：仅群主可操作，且不能禁言自己。
     */
    @Transactional
    public ModerationView muteMember(long operatorId, String groupId, long targetId,
                                     GroupMuteDuration duration) {
        requireGroup(groupId);
        GroupRole operatorRole = groupAccessGuard.requireRole(groupId, operatorId, GroupRole.OWNER);
        GroupMember target = requireMember(groupId, targetId);
        if (targetId == operatorId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot mute yourself");
        }
        if (!operatorRole.canMute(target.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot mute this member");
        }
        Instant now = timeProvider.now();
        // 解除一个本来就没被禁言的人不需要再刷一条提示。
        GroupMute before = moderationRepository.findMute(groupId, targetId).orElse(null);
        boolean wasMuted = before != null && before.activeAt(now);
        if (duration.unmute()) {
            moderationRepository.unmute(groupId, targetId);
        } else if (duration.permanent()) {
            moderationRepository.mute(groupId, targetId, null, now);
        } else {
            moderationRepository.mute(groupId, targetId, now.plus(duration.duration()), now);
        }
        if (!duration.unmute() || wasMuted) {
            String actor = nicknameOf(operatorId);
            String targetName = nicknameOf(targetId);
            String notice = duration.unmute()
                    ? actor + " 解除了 " + targetName + " 的禁言"
                    : actor + " 将 " + targetName + " " + mutePhrase(duration);
            systemMessageService.appendGroupNotice(groupId, operatorId, notice);
        }
        return view(groupId,
                moderationRepository.findSetting(groupId).orElse(GroupSetting.empty(groupId)),
                moderationRepository.findMute(groupId, targetId).orElse(null));
    }

    /**
     * 发送消息前的群禁言校验。永久禁言没有到期时间，必须单独判断。
     */
    public void ensureCanSend(String groupId, long senderId) {
        requireGroup(groupId);
        GroupMember sender = requireMember(groupId, senderId);
        GroupSetting setting = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        if (setting.muteAll() && !sender.role().manager()) {
            throw new BusinessException(ErrorCode.GROUP_MUTED, "group is muted");
        }
        GroupMute mute = moderationRepository.findMute(groupId, senderId).orElse(null);
        if (mute != null && mute.activeAt(timeProvider.now())) {
            throw new BusinessException(ErrorCode.GROUP_MUTED, mute.permanent()
                    ? "you are muted permanently"
                    : "you are muted until " + mute.mutedUntil());
        }
    }

    /**
     * 校验消息内容里的可选 mentions 数组，必须是群内成员且最多 20 个。
     */
    public void validateMentions(String groupId, byte[] content) {
        if (content == null || content.length == 0) {
            return;
        }
        String raw = new String(content, java.nio.charset.StandardCharsets.UTF_8).trim();
        if (!raw.startsWith("{")) {
            return;
        }
        try {
            var root = JsonUtils.mapper().readTree(content);
            var mentions = root.get("mentions");
            if (mentions == null || mentions.isNull()) {
                return;
            }
            if (!mentions.isArray() || mentions.size() > MAX_MENTIONS) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "mentions must be an array of at most " + MAX_MENTIONS);
            }
            List<Long> ids = new ArrayList<>();
            for (var node : mentions) {
                if (!node.canConvertToLong() || node.asLong() <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid mention id");
                }
                ids.add(node.asLong());
            }
            for (Long id : ids) {
                if (groupRepository.findMember(groupId, id).isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                            "mentioned user is not a group member: " + id);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid mentions");
        }
    }

    private ModerationView view(String groupId, GroupSetting setting, GroupMute mute) {
        return new ModerationView(groupId, setting.announcement(), setting.muteAll(),
                mute == null ? null : mute.mutedUntil(), mute != null && mute.permanent());
    }

    /** 群提示文案用昵称；用户不存在时退回 ID，避免整条提示丢失。 */
    private String nicknameOf(long userId) {
        return userRepository.findById(userId)
                .map(User::nickname)
                .filter(name -> name != null && !name.isBlank())
                .orElse("用户 " + userId);
    }

    private static String mutePhrase(GroupMuteDuration duration) {
        if (duration.permanent()) {
            return "永久禁言";
        }
        return "禁言 " + duration.duration().toMinutes() + " 分钟";
    }

    private Group requireGroup(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private GroupMember requireMember(String groupId, long userId) {
        return groupRepository.findMember(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER,
                        "not a group member"));
    }

    public record ModerationView(String groupId, String announcement, boolean muteAll,
                                 Instant mutedUntil, boolean mutedPermanently) {
    }
}
