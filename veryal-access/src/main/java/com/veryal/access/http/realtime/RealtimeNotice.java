package com.veryal.access.http.realtime;

import com.veryal.domain.message.event.MessageCreatedEvent;

/**
 * 推送给浏览器的新消息事件；ID 统一用字符串，避免 JS 精度丢失。
 */
public record RealtimeNotice(String type, String conversationId, String messageId,
                             long seq, String senderId, long createdAt) {

    public static RealtimeNotice message(MessageCreatedEvent event) {
        return new RealtimeNotice("message", event.conversationId(),
                Long.toString(event.messageId()), event.seq(),
                Long.toString(event.senderId()), event.createdAt().toEpochMilli());
    }
}
