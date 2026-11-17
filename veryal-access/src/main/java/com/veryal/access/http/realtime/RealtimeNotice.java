package com.veryal.access.http.realtime;

import com.veryal.domain.message.MessageType;
import com.veryal.domain.message.event.MessageCreatedEvent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 推送给浏览器的新消息事件。
 *
 * <p>小群/单聊携带完整消息体，前端可直接插入列表，省去一次回查 RTT；
 * 大群只推游标（{@code kind=cursor}），客户端按 seq 增量补齐，推送体积与成员数无关。
 * ID 统一用字符串，避免 JS 精度丢失。</p>
 */
public record RealtimeNotice(String kind, String conversationId, String messageId, long seq,
                             String senderId, String clientMsgId, String messageType,
                             String content, String contentEncoding, String createdAt,
                             String replyToId, String replySenderId, String replyPreview) {

    /** 携带完整消息体。 */
    public static final String KIND_MESSAGE = "message";

    /** 只携带游标，客户端按 seq 补齐。 */
    public static final String KIND_CURSOR = "cursor";

    /** 成员数超过该值时只推游标，避免大群推送体积随人数放大。 */
    public static final int FULL_PAYLOAD_MAX_RECIPIENTS = 200;

    public static RealtimeNotice message(MessageCreatedEvent event) {
        MessageType type = MessageType.fromCode(event.messageType());
        boolean text = type == MessageType.TEXT;
        String content = text
                ? new String(event.content(), StandardCharsets.UTF_8)
                : Base64.getEncoder().encodeToString(event.content());
        return new RealtimeNotice(KIND_MESSAGE, event.conversationId(),
                Long.toString(event.messageId()), event.seq(), Long.toString(event.senderId()),
                event.clientMessageId(), type.name(), content, text ? "utf8" : "base64",
                event.createdAt().toString(),
                event.replyToId() == null ? null : Long.toString(event.replyToId()),
                event.replySenderId() == null ? null : Long.toString(event.replySenderId()),
                event.replyPreview());
    }

    public static RealtimeNotice cursor(MessageCreatedEvent event) {
        return new RealtimeNotice(KIND_CURSOR, event.conversationId(), null, event.seq(),
                Long.toString(event.senderId()), event.clientMessageId(), null, null, null, null,
                null, null, null);
    }
}
