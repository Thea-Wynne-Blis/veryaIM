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

    /**
     * 按 user_id 升序分页读取成员 ID，用于大群异步扇出，避免一次性加载全部成员。
     */
    List<Long> findMemberIdsPage(String conversationId, long afterUserId, int limit);

    Optional<ConversationMember> findMember(String conversationId, long userId);

    /**
     * 读取成员级会话设置（置顶 / 免打扰 / 不显示 / 清空水位 / 手动未读）。
     * 非成员返回空。
     */
    Optional<ConversationMemberPrefs> findMemberPrefs(String conversationId, long userId);

    /**
     * 更新置顶 / 免打扰。传 {@code null} 表示该项不变。
     */
    void updateMemberFlags(String conversationId, long userId, Boolean pinned, Boolean muted);

    /**
     * 设置「不显示」：{@code hiddenAt} 为空表示取消不显示，恢复展示。
     */
    void updateMemberHidden(String conversationId, long userId, Instant hiddenAt);

    /**
     * 清空本人的聊天记录：把可见水位抬到 {@code sequence}，同时取消置顶与手动未读，
     * 并把会话标为不显示。消息行本身保留，会话另一方不受影响。
     */
    void clearMemberHistory(String conversationId, long userId, long sequence, Instant now);

    /** 手动标为未读 / 取消未读。 */
    void updateMemberManualUnread(String conversationId, long userId, boolean manualUnread);

    /**
     * 一次查询取回发送校验所需的上下文（类型、成员关系、禁言、私聊对端与拉黑状态）。
     * 会话不存在时返回空。
     */
    Optional<SendContext> findSendContext(String conversationId, long userId);

    void addMember(ConversationMember member);

    void removeMember(String conversationId, long userId);

    void updateMemberCursor(ConversationMember member);

    /**
     * 把自己的读游标推进到 {@code sequence}（只前进不后退），单条 UPDATE 完成。
     *
     * <p>用于「发送成功即视为已读」：否则自己刚发的消息会让会话列表显示未读。
     * 走 {@code GREATEST(last_read_seq, sequence)}，不需要先读后写。</p>
     */
    void advanceReadCursor(String conversationId, long userId, long sequence);

    /** 查询用户参与的会话，按更新时间倒序。 */
    List<Conversation> findByUser(long userId);

    /**
     * 批量取回用户所有会话的卡片数据（标题来源、未读游标、最后一条消息），一次查询完成。
     * 用于替代「每个会话各查 5 次」的 N+1 实现。
     */
    List<ConversationCardSnapshot> findCardSnapshots(long userId);

    /**
     * 删除会话及其全部成员（解散群使用）。
     */
    void delete(String conversationId);
}
