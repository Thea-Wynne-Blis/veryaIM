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

    void update(Group group);

    void touch(String groupId, Instant updatedAt);

    List<GroupMember> findMembers(String groupId);

    Optional<GroupMember> findMember(String groupId, long userId);

    void addMember(GroupMember member);

    void removeMember(String groupId, long userId);

    void updateMemberRole(String groupId, long userId, GroupRole role);

    long countMembers(String groupId);

    List<Group> findByUser(long userId);

    long count();

    List<Group> findAll(int limit);

    List<Group> search(String keyword, int limit);


    /**
     * 解散群：删除群与全部群成员。
     */
    void delete(String groupId);
}
