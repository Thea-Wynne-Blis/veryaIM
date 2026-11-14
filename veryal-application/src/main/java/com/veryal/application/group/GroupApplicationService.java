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

    private Group requireGroup(long tenantId, String groupId) {
        return groupRepository.findById(groupId)
                .filter(group -> group.tenantId() == tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "group not found"));
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
