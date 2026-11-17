package com.veryal.domain.conversation;

import java.time.Instant;

/**
 * 会话列表的成员级设置：置顶、免打扰、不显示与「清空记录」水位。
 *
 * <p>这些设置都只属于当前用户：{@code clearedSeq} 是本人可见的最低水位，
 * 只影响自己能否看到历史消息，不删除别人那份记录。</p>
 *
 * @param hiddenAt  「不显示」的设置时间；更新于它之后的消息会让会话重新出现
 * @param clearedSeq 本人已清空到的序号（{@code seq <= clearedSeq} 的消息对自己不可见）
 */
public record ConversationMemberPrefs(boolean pinned, boolean muted, Instant hiddenAt,
                                      long clearedSeq, boolean manualUnread) {

    /** 没有任何消息越过「不显示」时间点时，会话不展示。 */
    public boolean hidden(Instant lastMessageAt) {
        return hiddenAt != null && (lastMessageAt == null || !lastMessageAt.isAfter(hiddenAt));
    }

    /** 未读数：清空记录的水位也计入，手动未读至少为 1。 */
    public long unreadCount(long lastSeq, long lastReadSeq) {
        long floor = Math.max(lastReadSeq, clearedSeq);
        long unread = Math.max(0L, lastSeq - floor);
        return manualUnread ? Math.max(1L, unread) : unread;
    }

    /** 本人可见的第一条消息序号（该序号及更早的消息都已清空）。 */
    public long visibleFrom() {
        return clearedSeq;
    }
}
