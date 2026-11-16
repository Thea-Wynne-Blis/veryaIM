package com.veryal.access.http.dto;

import com.veryal.application.conversation.ConversationCard;

import java.time.Instant;

/**
 * 会话卡片响应。
 */
public record ConversationCardResponse(String conversationId,
                                       String type,
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

    public static ConversationCardResponse from(ConversationCard card) {
        return new ConversationCardResponse(card.conversationId(), card.type().name(),
                card.title(), card.peerUserId(), card.memberCount(), card.lastReadSeq(),
                card.lastSeq(), card.unreadCount(), card.lastMessageType(),
                card.lastMessagePreview(), card.lastMessageAt(), card.updatedAt());
    }
}
