package com.veryal.infrastructure.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.veryal.domain.group.Group;
import com.veryal.domain.group.GroupMember;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.group.GroupRole;
import com.veryal.infrastructure.mysql.SqlTime;
import com.veryal.infrastructure.mysql.mapper.GroupMapper;
import com.veryal.infrastructure.mysql.mapper.GroupMemberMapper;
import com.veryal.infrastructure.mysql.po.GroupMemberPO;
import com.veryal.infrastructure.mysql.po.GroupPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 群仓储 MyBatis-Plus 实现。
 */
@Repository
@RequiredArgsConstructor
public class MyBatisGroupRepository implements GroupRepository {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;

    @Override
    public Optional<Group> findById(String groupId) {
        return Optional.ofNullable(groupMapper.selectById(groupId)).map(this::toDomain);
    }

    @Override
    public boolean saveIfAbsent(Group group) {
        return groupMapper.insertIgnore(toPO(group)) > 0;
    }

    @Override
    public void update(Group group) {
        groupMapper.updateById(toPO(group));
    }

    @Override
    public void touch(String groupId, Instant updatedAt) {
        groupMapper.update(null, Wrappers.<GroupPO>lambdaUpdate()
                .eq(GroupPO::getId, groupId)
                .set(GroupPO::getUpdatedAt, SqlTime.toLocal(updatedAt)));
    }

    @Override
    public List<GroupMember> findMembers(String groupId) {
        return memberMapper.selectList(Wrappers.<GroupMemberPO>lambdaQuery()
                        .eq(GroupMemberPO::getGroupId, groupId)
                        .orderByAsc(GroupMemberPO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<GroupMember> findMember(String groupId, long userId) {
        return Optional.ofNullable(memberMapper.selectOne(Wrappers.<GroupMemberPO>lambdaQuery()
                        .eq(GroupMemberPO::getGroupId, groupId)
                        .eq(GroupMemberPO::getUserId, userId)))
                .map(this::toDomain);
    }

    @Override
    public void addMember(GroupMember member) {
        memberMapper.insertIgnore(toPO(member));
    }

    @Override
    public void removeMember(String groupId, long userId) {
        memberMapper.delete(Wrappers.<GroupMemberPO>lambdaQuery()
                .eq(GroupMemberPO::getGroupId, groupId)
                .eq(GroupMemberPO::getUserId, userId));
    }

    @Override
    public void updateMemberRole(String groupId, long userId, GroupRole role) {
        memberMapper.update(null, Wrappers.<GroupMemberPO>lambdaUpdate()
                .eq(GroupMemberPO::getGroupId, groupId)
                .eq(GroupMemberPO::getUserId, userId)
                .set(GroupMemberPO::getRole, role.code()));
    }

    @Override
    public long countMembers(String groupId) {
        Long count = memberMapper.selectCount(Wrappers.<GroupMemberPO>lambdaQuery()
                .eq(GroupMemberPO::getGroupId, groupId));
        return count == null ? 0L : count;
    }

    @Override
    public List<Group> findByUser(long tenantId, long userId) {
        return groupMapper.selectByUser(tenantId, userId).stream().map(this::toDomain).toList();
    }

    private Group toDomain(GroupPO po) {
        return new Group(po.id, po.tenantId, po.name, po.ownerId,
                SqlTime.toInstant(po.createdAt), SqlTime.toInstant(po.updatedAt));
    }

    private GroupPO toPO(Group group) {
        GroupPO po = new GroupPO();
        po.id = group.id();
        po.tenantId = group.tenantId();
        po.name = group.name();
        po.ownerId = group.ownerId();
        po.createdAt = SqlTime.toLocal(group.createdAt());
        po.updatedAt = SqlTime.toLocal(group.updatedAt());
        return po;
    }

    private GroupMember toDomain(GroupMemberPO po) {
        return new GroupMember(po.id, po.groupId, po.userId, GroupRole.fromCode(po.role),
                SqlTime.toInstant(po.joinedAt));
    }

    private GroupMemberPO toPO(GroupMember member) {
        GroupMemberPO po = new GroupMemberPO();
        po.id = member.id();
        po.groupId = member.groupId();
        po.userId = member.userId();
        po.role = member.role().code();
        po.joinedAt = SqlTime.toLocal(member.joinedAt());
        return po;
    }
}
