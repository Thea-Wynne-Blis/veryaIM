package com.veryal.application.conversation;

import com.veryal.domain.conversation.ConversationType;

import java.time.Instant;
import java.util.List;

/**
 * 会话视图。
 */
public record ConversationView(String conversationId, ConversationType type, List<Long> memberIds,
                               Instant createdAt, Instant updatedAt) {
}
