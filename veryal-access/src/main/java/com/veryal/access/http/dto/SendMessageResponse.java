package com.veryal.access.http.dto;

import com.veryal.application.message.SendMessageResult;

/**
 * 发送消息响应。
 */
public record SendMessageResponse(MessageResponse message, boolean duplicate) {

    public static SendMessageResponse from(SendMessageResult result) {
        return new SendMessageResponse(MessageResponse.from(result.message()), result.duplicate());
    }
}
