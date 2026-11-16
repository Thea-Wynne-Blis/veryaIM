package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.conversation.ConversationMember;

/**
 * 会话成员读游标。
 */
public record ReadReceiptResponse(
                                 @Schema(description = "用户 ID")
                                 long userId,
                                 @Schema(description = "已读到的消息序号")
                                 long readSequence
) {

    public static ReadReceiptResponse from(ConversationMember member) {
        return new ReadReceiptResponse(member.userId(), member.lastReadSeq());
    }
}
