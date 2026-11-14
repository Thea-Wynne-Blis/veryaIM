package com.veryal.access.http.dto;

import com.veryal.common.core.CursorPage;
import com.veryal.domain.message.Message;

import java.util.List;

/**
 * 游标分页消息响应。
 */
public record MessagePageResponse(List<MessageResponse> messages, String nextCursor, boolean hasMore) {

    public static MessagePageResponse from(CursorPage<Message> page) {
        return new MessagePageResponse(page.items().stream().map(MessageResponse::from).toList(),
                page.nextCursor(), page.hasMore());
    }
}
