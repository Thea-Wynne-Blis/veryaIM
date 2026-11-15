package com.veryal.application.group;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.group.GroupModerationRepository;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.group.GroupRole;
import com.veryal.domain.group.GroupSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 群公告、全员禁言、成员禁言与 @ 校验。
 */
@Service
@RequiredArgsConstructor
public class GroupModerationService {

    private static final int MAX_ANNOUNCEMENT = 2000;
    private static final int MAX_MENTIONS = 20;
    private static final long MAX_MUTE_MINUTES = 7 * 24 * 60;

    private final GroupModerationRepository moderationRepository;
    private final GroupRepository groupRepository;
    private final TimeProvider timeProvider;

    public ModerationView get(long userId, String groupId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);
        GroupSetting setting = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        Instant mutedUntil = moderationRepository.findMutedUntil(groupId, userId).orElse(null);
        return new ModerationView(groupId, setting.announcement(), setting.muteAll(), mutedUntil);
    }

    @Transactional
    public ModerationView updateAnnouncement(long operatorId, String groupId,
                                             String announcement) {
        Group group = requireGroup(groupId);
        requireManager(group, operatorId);
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
        return new ModerationView(groupId, updated.announcement(), updated.muteAll(),
                moderationRepository.findMutedUntil(groupId, operatorId).orElse(null));
    }

    @Transactional
    public ModerationView setMuteAll(long operatorId, String groupId,
                                     boolean muteAll) {
        Group group = requireGroup(groupId);
        requireManager(group, operatorId);
        GroupSetting current = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        GroupSetting updated = new GroupSetting(groupId, current.announcement(), muteAll,
                timeProvider.now(), operatorId);
        moderationRepository.saveSetting(updated);
        return new ModerationView(groupId, updated.announcement(), updated.muteAll(),
                moderationRepository.findMutedUntil(groupId, operatorId).orElse(null));
    }

    @Transactional
    public ModerationView muteMember(long operatorId, String groupId, long targetId,
                                     int minutes) {
        Group group = requireGroup(groupId);
        GroupMember operator = requireManager(group, operatorId);
        GroupMember target = requireMember(groupId, targetId);
        if (targetId == group.ownerId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot mute group owner");
        }
        if (operator.role() == GroupRole.ADMIN && target.role() == GroupRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "admin cannot mute another admin");
        }
        if (minutes < 0 || minutes > MAX_MUTE_MINUTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "mute minutes must be 0-" + MAX_MUTE_MINUTES);
        }
        Instant now = timeProvider.now();
        if (minutes == 0) {
            moderationRepository.unmute(groupId, targetId);
        } else {
            moderationRepository.mute(groupId, targetId, now.plus(Duration.ofMinutes(minutes)), now);
        }
        return new ModerationView(groupId,
                moderationRepository.findSetting(groupId).map(GroupSetting::announcement).orElse(null),
                moderationRepository.findSetting(groupId).map(GroupSetting::muteAll).orElse(false),
                moderationRepository.findMutedUntil(groupId, targetId).orElse(null));
    }

    public void ensureCanSend(String groupId, long senderId) {
        Group group = requireGroup(groupId);
        GroupMember sender = requireMember(groupId, senderId);
        GroupSetting setting = moderationRepository.findSetting(groupId)
                .orElse(GroupSetting.empty(groupId));
        if (setting.muteAll() && !sender.role().manager()) {
            throw new BusinessException(ErrorCode.GROUP_MUTED, "group is muted");
        }
        Instant mutedUntil = moderationRepository.findMutedUntil(groupId, senderId).orElse(null);
        if (mutedUntil != null && mutedUntil.isAfter(timeProvider.now())) {
            throw new BusinessException(ErrorCode.GROUP_MUTED, "you are muted until " + mutedUntil);
        }
    }

    /**
     * 文本消息可选 mentions 数组，必须是本群成员，最多 20 个。
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

    private Group requireGroup(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private GroupMember requireMember(String groupId, long userId) {
        return groupRepository.findMember(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER,
                        "not a group member"));
    }

    private GroupMember requireManager(Group group, long userId) {
        GroupMember member = requireMember(group.id(), userId);
        if (!member.role().manager()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner or admin can manage group");
        }
        return member;
    }

    public record ModerationView(String groupId, String announcement, boolean muteAll,
                                 Instant mutedUntil) {
    }
}
