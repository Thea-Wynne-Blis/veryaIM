package com.veryal.access.http.dto;

import com.veryal.application.conversation.ConversationSummary;
import com.veryal.application.conversation.ConversationView;

import java.time.Instant;
import java.util.List;

/**
 * 会话响应。unreadCount = lastSeq - lastReadSeq。
 */
public record ConversationResponse(String conversationId, String type, List<Long> memberIds,
                                   long lastReadSeq, long lastSeq, long unreadCount,
                                   Instant createdAt, Instant updatedAt) {

    public static ConversationResponse from(ConversationView view) {
        return new ConversationResponse(view.conversationId(), view.type().name(), view.memberIds(),
                0, 0, 0, view.createdAt(), view.updatedAt());
    }

    public static ConversationResponse from(ConversationSummary summary) {
        return new ConversationResponse(summary.conversationId(), summary.type().name(), List.of(),
                summary.lastReadSeq(), summary.lastSeq(),
                Math.max(0, summary.lastSeq() - summary.lastReadSeq()),
                null, summary.updatedAt());
    }
}
