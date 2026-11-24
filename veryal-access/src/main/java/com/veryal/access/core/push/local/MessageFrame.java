package com.veryal.access.core.push.local;

import com.google.protobuf.ByteString;
import com.veryal.access.http.realtime.RealtimeNotice;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.protocol.protobuf.MessageNotify;

/**
 * 一条消息对单个连接的下行帧。
 *
 * <p>同一事件既可能在落库后由本节点直推（{@link RealtimeDelivery}），也可能经 MQ 网关
 * 下发（{@link LocalGatewayPush}）；两条链路共用这里的构造逻辑，保证 Protobuf 帧与
 * 浏览器 JSON 帧内容一致，{@code eventId} 用于写入连接前按连接去重。</p>
 */
public record MessageFrame(String eventId, byte[] protobuf, RealtimeNotice text) {

    public static MessageFrame of(MessageCreatedEvent message) {
        MessageNotify notify = MessageNotify.newBuilder()
                .setMessageId(message.messageId())
                .setConversationId(message.conversationId())
                .setSenderId(message.senderId())
                .setSequence(message.seq())
                .setMessageType(message.messageType())
                .setContent(ByteString.copyFrom(message.content()))
                .setCreatedAt(message.createdAt().toEpochMilli())
                .setClientMessageId(message.clientMessageId() == null ? "" : message.clientMessageId())
                .build();
        boolean fullPayload = message.recipientUserIds() == null
                || message.recipientUserIds().size() <= RealtimeNotice.FULL_PAYLOAD_MAX_RECIPIENTS;
        return new MessageFrame(message.eventId(), notify.toByteArray(),
                fullPayload ? RealtimeNotice.message(message) : RealtimeNotice.cursor(message));
    }
}
