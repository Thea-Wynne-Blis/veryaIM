package com.veryal.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储端口。
 */
public interface ConversationRepository {

    Optional<Conversation> findById(String conversationId);

    /**
     * 幂等创建会话，已存在时返回 {@code false}。
     */
    boolean saveIfAbsent(Conversation conversation);

    void touch(String conversationId, Instant updatedAt);

    List<ConversationMember> findMembers(String conversationId);

    Optional<ConversationMember> findMember(String conversationId, long userId);

    void addMember(ConversationMember member);

    void removeMember(String conversationId, long userId);

    void updateMemberCursor(ConversationMember member);

    /** 查询用户参与的会话，按更新时间倒序。 */
    List<Conversation> findByUser(long tenantId, long userId);

    /**
     * 删除会话及其全部成员（解散群使用）。
     */
    void delete(String conversationId);
}
