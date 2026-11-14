package com.veryal.domain.group;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 群仓储端口。
 */
public interface GroupRepository {

    Optional<Group> findById(String groupId);

    boolean saveIfAbsent(Group group);

    void touch(String groupId, Instant updatedAt);

    List<GroupMember> findMembers(String groupId);

    Optional<GroupMember> findMember(String groupId, long userId);

    void addMember(GroupMember member);

    long countMembers(String groupId);

    List<Group> findByUser(long tenantId, long userId);
}
