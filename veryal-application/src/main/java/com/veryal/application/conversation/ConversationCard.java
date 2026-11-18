package com.veryal.application.conversation;

import com.veryal.domain.conversation.ConversationType;

import java.time.Instant;

/**
 * 会话卡片：会话列表直接渲染所需全部展示信息。
 *
 * <p>单聊的 {@code title} 是对方昵称（有好友备注时用备注），群聊是群名；{@code lastMessagePreview}
 * 已经按消息类型转成可读文本，{@code lastMessageSenderId/Name} 供群聊
 * 渲染成「姓名：内容」，为空表示这段会话还没有真实消息（系统提示不算）。</p>
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
                               Instant updatedAt,
                               boolean pinned,
                               boolean muted,
                               boolean hidden,
                               long lastMessageSenderId,
                               String lastMessageSenderName) {
}
