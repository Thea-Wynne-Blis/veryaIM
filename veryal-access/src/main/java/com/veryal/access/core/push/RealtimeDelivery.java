package com.veryal.access.core.push;

import com.google.protobuf.ByteString;
import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.delivery.RealtimeDeliveryPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.MessageNotify;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 本节点实时投递实现。
 *
 * <p>单机模式下由命令 Handler 直接调用；MQ 模式下由投递消费者调用。
 * 只向本节点在线连接写 Channel，跨节点由每个实例各自消费投递事件完成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeDelivery implements RealtimeDeliveryPort {

    private final ConnectionRegistry connectionRegistry;

    @Override
    public void deliver(MessageCreatedEvent event) {
        MessageNotify notify = MessageNotify.newBuilder()
                .setMessageId(event.messageId())
                .setConversationId(event.conversationId())
                .setSenderId(event.senderId())
                .setSequence(event.seq())
                .setMessageType(event.messageType())
                .setContent(ByteString.copyFrom(event.content()))
                .setCreatedAt(event.createdAt().toEpochMilli())
                .setClientMessageId(event.clientMessageId() == null ? "" : event.clientMessageId())
                .build();
        byte[] payload = notify.toByteArray();
        Set<String> pushed = new HashSet<>();
        for (Long recipientUserId : event.recipientUserIds()) {
            pushToUser(recipientUserId, event, payload, pushed);
        }
        // 发送者的其它设备同步该消息；发送设备本身跳过。
        pushToUser(event.senderId(), event, payload, pushed);
    }

    private void pushToUser(long userId, MessageCreatedEvent event, byte[] payload, Set<String> pushed) {
        for (Connection connection : connectionRegistry.findByUserId(userId)) {
            if (!connection.authenticated()) {
                continue;
            }
            if (userId == event.senderId() && event.senderDeviceId() != null
                    && event.senderDeviceId().equals(connection.deviceId())) {
                continue;
            }
            if (!pushed.add(connection.connectionId())) {
                continue;
            }
            connection.send(ProtocolMessage.of(Command.MESSAGE_NOTIFY, connection.nextSequence(),
                    null, null, payload));
            log.debug("message pushed: userId={}, connectionId={}, messageId={}",
                    userId, connection.connectionId(), event.messageId());
        }
    }
}
