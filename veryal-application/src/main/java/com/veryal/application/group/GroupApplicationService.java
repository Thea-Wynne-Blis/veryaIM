package com.veryal.application.group;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.conversation.Conversation;
import com.veryal.domain.conversation.ConversationMember;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
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
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public GroupView create(long tenantId, long ownerId, String name, List<Long> memberIds) {
        String validName = validateName(name);
        List<Long> invitees = normalizeInvitees(memberIds, ownerId);
        if (1 + invitees.size() > Group.MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "group member limit is " + Group.MAX_MEMBERS);
        }
        ensureUsersExist(invitees);
        Instant now = timeProvider.now();
        Conversation conversation = Conversation.createGroup(tenantId, idGenerator.nextId(), now);
        if (!conversationRepository.saveIfAbsent(conversation)) {
            throw new BusinessException(ErrorCode.CONFLICT, "group conversation already exists");
        }
        addConversationMember(conversation.id(), ownerId, now);
        Group group = Group.create(conversation.id(), tenantId, validName, ownerId, now);
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
        return view(group);
    }

    @Transactional
    public GroupView addMembers(long tenantId, long operatorId, String groupId, List<Long> userIds) {
        Group group = requireGroup(tenantId, groupId);
        GroupMember operator = groupRepository.findMember(groupId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER,
                        "not a group member"));
        if (!operator.role().manager()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner or admin can add members");
        }
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
        return view(groupRepository.findById(groupId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public GroupView get(long tenantId, long userId, String groupId) {
        Group group = requireGroup(tenantId, groupId);
        if (groupRepository.findMember(groupId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER, "not a group member");
        }
        return view(group);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(long tenantId, long userId) {
        return groupRepository.findByUser(tenantId, userId).stream()
                .map(group -> new GroupSummary(group.id(), group.name(), group.ownerId(),
                        groupRepository.countMembers(group.id()), group.updatedAt()))
                .toList();
    }

    @Transactional
    public GroupView rename(long tenantId, long operatorId, String groupId, String name) {
        Group group = requireGroup(tenantId, groupId);
        GroupMember operator = requireMember(groupId, operatorId);
        if (!operator.role().manager()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner or admin can rename group");
        }
        Group renamed = group.rename(validateName(name), timeProvider.now());
        groupRepository.update(renamed);
        return view(renamed);
    }

    /**
     * 退群。群主必须先转让群主或解散群，不能直接退出。
     */
    @Transactional
    public void leave(long tenantId, long userId, String groupId) {
        Group group = requireGroup(tenantId, groupId);
        requireMember(groupId, userId);
        if (group.ownerId() == userId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "owner cannot leave group, transfer ownership first");
        }
        groupRepository.removeMember(groupId, userId);
        conversationRepository.removeMember(groupId, userId);
        groupRepository.touch(groupId, timeProvider.now());
    }

    /**
     * 踢人。管理员不能踢管理员或群主，只有群主可以处理管理员。
     */
    @Transactional
    public GroupView kick(long tenantId, long operatorId, String groupId, long targetUserId) {
        Group group = requireGroup(tenantId, groupId);
        GroupMember operator = requireMember(groupId, operatorId);
        if (!operator.role().manager()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner or admin can remove members");
        }
        if (targetUserId == group.ownerId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "cannot remove group owner");
        }
        GroupMember target = requireMember(groupId, targetUserId);
        if (operator.role() == GroupRole.ADMIN && target.role() == GroupRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "admin cannot remove another admin");
        }
        groupRepository.removeMember(groupId, targetUserId);
        conversationRepository.removeMember(groupId, targetUserId);
        groupRepository.touch(groupId, timeProvider.now());
        return view(groupRepository.findById(groupId).orElseThrow());
    }

    /**
     * 设置成员角色。只有群主可以设置管理员，且不能转移群主。
     */
    @Transactional
    public GroupView changeRole(long tenantId, long operatorId, String groupId, long targetUserId,
                                GroupRole role) {
        Group group = requireGroup(tenantId, groupId);
        GroupMember operator = requireMember(groupId, operatorId);
        if (operator.role() != GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner can change member role");
        }
        if (targetUserId == group.ownerId() || role == GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot change owner role");
        }
        requireMember(groupId, targetUserId);
        groupRepository.updateMemberRole(groupId, targetUserId, role);
        groupRepository.touch(groupId, timeProvider.now());
        return view(groupRepository.findById(groupId).orElseThrow());
    }

    /**
     * 转让群主：原群主降为管理员，新群主必须是群成员。
     */
    @Transactional
    public GroupView transferOwner(long tenantId, long currentOwnerId, String groupId, long newOwnerId) {
        Group group = requireGroup(tenantId, groupId);
        GroupMember operator = requireMember(groupId, currentOwnerId);
        if (operator.role() != GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner can transfer ownership");
        }
        if (newOwnerId == currentOwnerId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "already group owner");
        }
        requireMember(groupId, newOwnerId);
        Instant now = timeProvider.now();
        Group transferred = group.transferOwner(newOwnerId, now);
        groupRepository.update(transferred);
        groupRepository.updateMemberRole(groupId, newOwnerId, GroupRole.OWNER);
        groupRepository.updateMemberRole(groupId, currentOwnerId, GroupRole.ADMIN);
        groupRepository.touch(groupId, now);
        return view(transferred);
    }

    /**
     * 解散群：仅群主可操作，删除群、群成员、会话与会话成员；历史消息保留。
     */
    @Transactional
    public void disband(long tenantId, long ownerId, String groupId) {
        requireGroup(tenantId, groupId);
        GroupMember operator = requireMember(groupId, ownerId);
        if (operator.role() != GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only owner can disband group");
        }
        conversationRepository.delete(groupId);
        groupRepository.delete(groupId);
    }

    private Group requireGroup(long tenantId, String groupId) {
        return groupRepository.findById(groupId)
                .filter(group -> group.tenantId() == tenantId)
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
                group.createdAt(), group.updatedAt());
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
