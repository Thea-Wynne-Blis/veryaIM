package com.veryal.access.http.dto;

import com.veryal.domain.conversation.ConversationMember;

/**
 * 会话成员读游标。
 */
public record ReadReceiptResponse(long userId, long readSequence) {

    public static ReadReceiptResponse from(ConversationMember member) {
        return new ReadReceiptResponse(member.userId(), member.lastReadSeq());
    }
}
