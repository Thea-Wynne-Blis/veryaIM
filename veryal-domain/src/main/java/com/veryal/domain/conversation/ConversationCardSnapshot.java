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
                                       String groupName) {
}
