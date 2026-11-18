package com.veryal.application.group;

import com.veryal.application.audit.AuditRecorder;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.audit.AuditAction;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.group.GroupModerationRepository;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.group.GroupRole;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 群应用服务：创建群、加成员、查询群。
 *
 * <p>群与群会话同 ID：群成员变更必须同步会话成员，消息权限统一由会话成员校验。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupApplicationService {

    private final GroupRepository groupRepository;
    private final GroupModerationRepository moderationRepository;
    private final GroupAccessGuard groupAccessGuard;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final AuditRecorder auditRecorder;

    @Transactional
    public GroupView create(long ownerId, String name, List<Long> memberIds) {
        String validName = validateName(name);
        List<Long> invitees = normalizeInvitees(memberIds, ownerId);
        if (1 + invitees.size() > Group.MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "group member limit is " + Group.MAX_MEMBERS);
        }
        ensureUsersExist(invitees);
        Instant now = timeProvider.now();
        Conversation conversation = Conversation.createGroup(idGenerator.nextId(), now);
        if (!conversationRepository.saveIfAbsent(conversation)) {
            throw new BusinessException(ErrorCode.CONFLICT, "group conversation already exists");
        }
        addConversationMember(conversation.id(), ownerId, now);
        Group group = Group.create(conversation.id(), validName, ownerId, now);
        if (!groupRepository.saveIfAbsent(group)) {
            throw new BusinessException(ErrorCode.CONFLICT, "group already exists");
        }
        groupRepository.addMember(GroupMember.join(idGenerator.nextId(), group.id(), ownerId,
                GroupRole.OWNER, now));
        for (Long invitee : invitees) {
            addConversationMember(conversation.id(), invitee, now);
            groupRepository.addMember(GroupMember.join(idGenerator.nextId(), group.id(), invitee,
                    GroupRole.MEMBER, now));
        }
        auditRecorder.record(ownerId, AuditAction.GROUP_CREATE, "group", group.id(),
                "members=" + (1 + invitees.size()) + ",name=" + validName);
        return view(group);
    }

    @Transactional
    public GroupView addMembers(long operatorId, String groupId, List<Long> userIds) {
        Group group = requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.ADMIN);
        List<Long> invitees = normalizeInvitees(userIds, operatorId);
        long existing = groupRepository.countMembers(groupId);
        if (existing + invitees.size() > Group.MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "group member limit is " + Group.MAX_MEMBERS);
        }
        ensureUsersExist(invitees);
        Instant now = timeProvider.now();
        for (Long invitee : invitees) {
            addConversationMember(groupId, invitee, now);
            groupRepository.addMember(GroupMember.join(idGenerator.nextId(), groupId, invitee,
                    GroupRole.MEMBER, now));
        }
        groupRepository.touch(groupId, now);
        auditRecorder.record(operatorId, AuditAction.GROUP_MEMBER_ADD, "group", groupId,
                "members=" + invitees.size());
        return view(groupRepository.findById(groupId).orElseThrow(), operatorId);
    }

    @Transactional(readOnly = true)
    public GroupView get(long userId, String groupId) {
        Group group = requireGroup(groupId);
        if (groupRepository.findMember(groupId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER, "not a group member");
        }
        return view(group, userId);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(long userId) {
        return groupRepository.findByUser(userId).stream()
                .map(group -> new GroupSummary(group.id(), group.name(), group.ownerId(),
                        groupRepository.countMembers(group.id()), group.updatedAt()))
                .toList();
    }

    @Transactional
    public GroupView rename(long operatorId, String groupId, String name) {
        Group group = requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.ADMIN);
        Group renamed = group.rename(validateName(name), timeProvider.now());
        groupRepository.update(renamed);
        auditRecorder.record(operatorId, AuditAction.GROUP_RENAME, "group", groupId,
                "name=" + renamed.name());
        return view(renamed, operatorId);
    }

    /**
     * 退群。群主必须先转让群主或解散群，不能直接退出。
     */
    @Transactional
    public void leave(long userId, String groupId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);
        if (group.ownerId() == userId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "owner cannot leave group, transfer ownership first");
        }
        groupRepository.removeMember(groupId, userId);
        conversationRepository.removeMember(groupId, userId);
        moderationRepository.unmute(groupId, userId);
        groupRepository.touch(groupId, timeProvider.now());
        auditRecorder.record(userId, AuditAction.GROUP_MEMBER_LEAVE, "group", groupId,
                "userId=" + userId);
    }

    /**
     * 踢人：群主可移出管理员与成员，管理员只能移出普通成员。
     */
    @Transactional
    public GroupView kick(long operatorId, String groupId, long targetUserId) {
        Group group = requireGroup(groupId);
        GroupRole operatorRole = groupAccessGuard.requireRole(groupId, operatorId, GroupRole.ADMIN);
        if (targetUserId == group.ownerId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot remove group owner");
        }
        GroupMember target = requireMember(groupId, targetUserId);
        if (!operatorRole.canKick(target.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot remove this member");
        }
        groupRepository.removeMember(groupId, targetUserId);
        conversationRepository.removeMember(groupId, targetUserId);
        moderationRepository.unmute(groupId, targetUserId);
        groupRepository.touch(groupId, timeProvider.now());
        auditRecorder.record(operatorId, AuditAction.GROUP_MEMBER_REMOVE, "group", groupId,
                "userId=" + targetUserId);
        return view(groupRepository.findById(groupId).orElseThrow(), operatorId);
    }

    /**
     * 设置成员角色。只有群主可以设置管理员，且不能转移群主。
     */
    @Transactional
    public GroupView changeRole(long operatorId, String groupId, long targetUserId,
                                GroupRole role) {
        Group group = requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.OWNER);
        if (targetUserId == group.ownerId() || role == GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot change owner role");
        }
        requireMember(groupId, targetUserId);
        groupRepository.updateMemberRole(groupId, targetUserId, role);
        groupRepository.touch(groupId, timeProvider.now());
        auditRecorder.record(operatorId, AuditAction.GROUP_ROLE_CHANGE, "group_member",
                groupId + ":" + targetUserId, "role=" + role.name());
        return view(groupRepository.findById(groupId).orElseThrow(), operatorId);
    }

    /**
     * 转让群主：新群主必须是群成员，原群主降为普通成员（不再保留管理员权限）。
     */
    @Transactional
    public GroupView transferOwner(long currentOwnerId, String groupId, long newOwnerId) {
        Group group = requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, currentOwnerId, GroupRole.OWNER);
        if (newOwnerId == currentOwnerId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "already group owner");
        }
        requireMember(groupId, newOwnerId);
        Instant now = timeProvider.now();
        Group transferred = group.transferOwner(newOwnerId, now);
        groupRepository.update(transferred);
        groupRepository.updateMemberRole(groupId, newOwnerId, GroupRole.OWNER);
        groupRepository.updateMemberRole(groupId, currentOwnerId, GroupRole.MEMBER);
        groupRepository.touch(groupId, now);
        auditRecorder.record(currentOwnerId, AuditAction.GROUP_OWNER_TRANSFER, "group", groupId,
                "newOwner=" + newOwnerId);
        return view(transferred, currentOwnerId);
    }

    /**
     * 解散群：仅群主可操作，删除群、群成员、会话与会话成员；历史消息保留。
     */
    @Transactional
    public void disband(long ownerId, String groupId) {
        requireGroup(groupId);
        groupAccessGuard.requireRole(groupId, ownerId, GroupRole.OWNER);
        auditRecorder.record(ownerId, AuditAction.GROUP_DISBAND, "group", groupId, null);
        moderationRepository.deleteGroupState(groupId);
        conversationRepository.delete(groupId);
        groupRepository.delete(groupId);
    }

    private Group requireGroup(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "group not found"));
    }

    private GroupMember requireMember(String groupId, long userId) {
        return groupRepository.findMember(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER,
                        "not a group member"));
    }

    private void addConversationMember(String conversationId, long userId, Instant now) {
        conversationRepository.addMember(ConversationMember.join(idGenerator.nextId(), conversationId,
                userId, now));
    }

    private GroupView view(Group group) {
        List<Long> memberIds = groupRepository.findMembers(group.id()).stream()
                .map(GroupMember::userId)
                .toList();
        return new GroupView(group.id(), group.name(), group.ownerId(), memberIds,
                group.createdAt(), group.updatedAt(), null);
    }

    /**
     * 群详情：附带查看者在群内的角色，前端据此决定显示哪些管理操作。
     */
    private GroupView view(Group group, long viewerId) {
        GroupView base = view(group);
        String role = groupRepository.findMember(group.id(), viewerId)
                .map(member -> member.role().name())
                .orElse(null);
        return new GroupView(base.groupId(), base.name(), base.ownerId(), base.memberIds(),
                base.createdAt(), base.updatedAt(), role);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.length() > Group.MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid group name");
        }
        return name.trim();
    }

    private List<Long> normalizeInvitees(List<Long> userIds, long ownerId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid user id");
            }
            if (userId != ownerId) {
                distinct.add(userId);
            }
        }
        return new ArrayList<>(distinct);
    }

    private void ensureUsersExist(List<Long> userIds) {
        for (Long userId : userIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                            "user not found: " + userId));
            user.ensureActive();
        }
    }
}
