package com.veryal.application.conversation;

import com.veryal.domain.conversation.ConversationType;

import java.time.Instant;

/**
 * 会话列表项：包含当前用户读游标与会话最大序号，客户端据此计算未读数。
 */
public record ConversationSummary(String conversationId, ConversationType type, long lastReadSeq,
                                  long lastSeq, Instant updatedAt) {
}
