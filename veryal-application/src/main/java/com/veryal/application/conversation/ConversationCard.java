package com.veryal.application.conversation;

import com.veryal.domain.conversation.ConversationType;

import java.time.Instant;

/**
 * 会话卡片：会话列表直接渲染所需的全部展示信息。
 *
 * <p>单聊的 {@code title} 是对方昵称，群聊是群名；{@code lastMessagePreview}
 * 已按类型做了人类可读转换并截断。</p>
 */
public record ConversationCard(String conversationId,
                               ConversationType type,
                               String title,
                               long peerUserId,
                               int memberCount,
                               long lastReadSeq,
                               long lastSeq,
                               long unreadCount,
                               String lastMessageType,
                               String lastMessagePreview,
                               Instant lastMessageAt,
                               Instant updatedAt) {
}
