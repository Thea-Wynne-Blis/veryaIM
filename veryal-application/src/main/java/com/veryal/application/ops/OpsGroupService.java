package com.veryal.application.ops;

import com.veryal.application.platform.PlatformAuditRecorder;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 运营后台群管理。
 */
@Service
@RequiredArgsConstructor
public class OpsGroupService {

    private final GroupRepository groupRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final PlatformAuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public List<OpsGroupView> search(String keyword, int limit) {
        String value = keyword == null ? "" : keyword.trim();
        return groupRepository.search(value,
                        Math.max(1, Math.min(limit, 100))).stream()
                .map(group -> new OpsGroupView(group.id(), group.name(), group.ownerId(),
                        groupRepository.countMembers(group.id()), group.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OpsGroupDetail detail(String groupId) {
        Group group = require(groupId);
        List<OpsMemberView> members = groupRepository.findMembers(groupId).stream()
                .map(member -> toMemberView(member))
                .toList();
        return new OpsGroupDetail(group.id(), group.name(), group.ownerId(),
                group.createdAt(), members);
    }

    @Transactional
    public void disband(long adminId, String groupId, String reason) {
        require(groupId);
        conversationRepository.delete(groupId);
        groupRepository.delete(groupId);
        auditRecorder.record(adminId, "OPS_GROUP_DISBAND", "group", groupId,
                "reason=" + (reason == null ? "" : reason));
    }

    private OpsMemberView toMemberView(GroupMember member) {
        User user = userRepository.findById(member.userId()).orElse(null);
        return new OpsMemberView(member.userId(), user == null ? "" : user.nickname(),
                user == null ? null : user.avatar(), member.role().name(), member.joinedAt());
    }

    private Group require(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    public record OpsGroupView(String groupId, String name, long ownerId, long memberCount,
                               Instant createdAt) {
    }

    public record OpsGroupDetail(String groupId, String name, long ownerId, Instant createdAt,
                                 List<OpsMemberView> members) {
    }

    public record OpsMemberView(long userId, String nickname, String avatar, String role,
                                Instant joinedAt) {
    }
}
