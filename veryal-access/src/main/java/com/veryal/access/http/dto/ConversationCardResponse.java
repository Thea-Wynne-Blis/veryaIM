package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.conversation.ConversationCard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 会话卡片响应。
 */
public record ConversationCardResponse(
                                 @Schema(description = "会话 ID。单聊形如 p_{用户A}_{用户B}，群聊即群 ID")
                                 String conversationId,
                                 @Schema(description = "类型")
                                 String type,
                                 @Schema(description = "会话标题。单聊为对方昵称，群聊为群名")
                                 String title,
                                 @Schema(description = "对方用户 ID")
                                 long peerUserId,
                                 @Schema(description = "成员数量")
                                 int memberCount,
                                 @Schema(description = "当前用户已读到的序号")
                                 long lastReadSeq,
                                 @Schema(description = "会话当前最大序号")
                                 long lastSeq,
                                 @Schema(description = "未读消息数 = lastSeq 减 lastReadSeq")
                                 long unreadCount,
                                 @Schema(description = "最后一条消息的类型")
                                 String lastMessageType,
                                 @Schema(description = "最后一条消息的预览文本")
                                 String lastMessagePreview,
                                 @Schema(description = "最后一条消息的时间")
                                 Instant lastMessageAt,
                                 @Schema(description = "更新时间（UTC）")
                                 Instant updatedAt,
                                 @Schema(description = "当前用户是否置顶了该会话")
                                 boolean pinned,
                                 @Schema(description = "当前用户是否对会话开启了消息免打扰")
                                 boolean muted,
                                 @Schema(description = "当前用户是否把会话设为「不显示」")
                                 boolean hidden
) {

    public static ConversationCardResponse from(ConversationCard card) {
        return new ConversationCardResponse(card.conversationId(), card.type().name(),
                card.title(), card.peerUserId(), card.memberCount(), card.lastReadSeq(),
                card.lastSeq(), card.unreadCount(), card.lastMessageType(),
                card.lastMessagePreview(), card.lastMessageAt(), card.updatedAt(),
                card.pinned(), card.muted(), card.hidden());
    }
}
