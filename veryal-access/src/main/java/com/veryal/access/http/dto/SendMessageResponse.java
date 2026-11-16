package com.veryal.access.http.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.veryal.application.message.SendMessageResult;

/**
 * 发送消息响应。
 */
public record SendMessageResponse(
                                 @Schema(description = "提示信息，可直接展示")
                                 MessageResponse message,
                                 boolean duplicate
) {

    public static SendMessageResponse from(SendMessageResult result) {
        return new SendMessageResponse(MessageResponse.from(result.message()), result.duplicate());
    }
}
