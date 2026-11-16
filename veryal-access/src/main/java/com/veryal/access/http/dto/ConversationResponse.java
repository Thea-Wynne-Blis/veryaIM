package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.conversation.ConversationSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.conversation.ConversationView;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 会话响应。unreadCount = lastSeq - lastReadSeq。
 */
public record ConversationResponse(
                                 @Schema(description = "会话 ID。单聊形如 p_{用户A}_{用户B}，群聊即群 ID")
                                 String conversationId,
                                 @Schema(description = "类型")
                                 String type,
                                 @Schema(description = "成员用户 ID 列表")
                                 List<Long> memberIds,
                                 @Schema(description = "当前用户已读到的序号")
                                 long lastReadSeq,
                                 @Schema(description = "会话当前最大序号")
                                 long lastSeq,
                                 @Schema(description = "未读消息数 = lastSeq 减 lastReadSeq")
                                 long unreadCount,
                                 @Schema(description = "创建时间（UTC）")
                                 Instant createdAt,
                                 @Schema(description = "更新时间（UTC）")
                                 Instant updatedAt
) {

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
