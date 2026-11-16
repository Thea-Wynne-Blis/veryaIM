package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.common.core.CursorPage;
import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.domain.message.Message;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 游标分页消息响应。
 */
public record MessagePageResponse(
                                 @Schema(description = "消息列表")
                                 List<MessageResponse> messages,
                                 @Schema(description = "下一页游标")
                                 String nextCursor,
                                 @Schema(description = "是否还有更多")
                                 boolean hasMore
) {

    public static MessagePageResponse from(CursorPage<Message> page) {
        return new MessagePageResponse(page.items().stream().map(MessageResponse::from).toList(),
                page.nextCursor(), page.hasMore());
    }
}
