package com.veryal.access.core.push;

import com.google.protobuf.ByteString;
import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.access.http.realtime.RealtimeNotice;
import com.veryal.domain.delivery.GatewayPushPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.MessageNotify;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 connectionId 向本节点连接推送消息。
 *
 * <p>MQ 路由模式下，投递消费者通过网关交换机下发 {@link com.veryal.domain.delivery.GatewayPushCommand}，
 * 本节点只处理属于自己的 connectionId。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalGatewayPush implements GatewayPushPort {

    private final ConnectionRegistry connectionRegistry;
    private final ConnectionPushDedup connectionPushDedup;

    @Override
    public void push(MessageCreatedEvent message, List<String> connectionIds) {
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
        byte[] payload = notify.toByteArray();
        boolean fullPayload = message.recipientUserIds() == null
                || message.recipientUserIds().size() <= RealtimeNotice.FULL_PAYLOAD_MAX_RECIPIENTS;
        RealtimeNotice textNotice = fullPayload
                ? RealtimeNotice.message(message)
                : RealtimeNotice.cursor(message);
        for (String connectionId : connectionIds) {
            Connection connection = connectionRegistry.find(connectionId);
            if (connection == null || !connection.authenticated()) {
                log.debug("skip gateway push, connection unavailable: connectionId={}", connectionId);
                continue;
            }
            // 本节点直推可能已经发过同一条消息，共用去重表避免重复。
            if (!connectionPushDedup.firstDelivery(message.eventId(), connectionId)) {
                continue;
            }
            if (connection.textFrameMode()) {
                connection.sendJson("message", textNotice);
            } else {
                connection.send(ProtocolMessage.of(Command.MESSAGE_NOTIFY, connection.nextSequence(),
                        null, null, payload));
            }
        }
    }
}
