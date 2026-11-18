package com.veryal.domain.conversation;

import java.time.Instant;

/**
 * 会话卡片所需的原始数据快照，用一条联表查询批量取回。
 *
 * <p>原来 {@code listCards} 对每个会话分别查成员、本人游标、对端资料、最大序号和最后一条消息，
 * 一个用户有 30 个会话就是约 150 次数据库往返。这里把全部字段合并成一次查询，
 * 往返次数从 O(会话数) 降为 O(1)。</p>
 *
 * @param lastMessageTypeCode 最后一条消息的类型码，无消息时为 {@code null}
 * @param lastMessageContent  最后一条消息的正文，无消息时为 {@code null}
 * @param lastMessageRecalled 最后一条消息是否已撤回
 * @param clearedSeq          本人清空记录的水位
 * @param hidden              本人是否选择了「不显示」（更晚的消息会让它重新出现）
 * @param peerRemark          本人给单聊对方设的好友备注，没设为 {@code null}
 */
public record ConversationCardSnapshot(String conversationId,
                                       ConversationType type,
                                       Instant updatedAt,
                                       long lastReadSeq,
                                       long lastSeq,
                                       int memberCount,
                                       Integer lastMessageTypeCode,
                                       byte[] lastMessageContent,
                                       boolean lastMessageRecalled,
                                       Instant lastMessageAt,
                                       Long peerUserId,
                                       String peerNickname,
                                       String peerRemark,
                                       String groupName,
                                       long clearedSeq,
                                       boolean manualUnread,
                                       boolean pinned,
                                       boolean muted,
                                       boolean hidden,
                                       // 最后一条真实消息的发送者：群聊预览显示「姓名：内容」用。
                                       Long lastMessageSenderId,
                                       String lastMessageSenderName) {
}
