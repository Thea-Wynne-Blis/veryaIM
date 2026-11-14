package com.veryal.access.core.push;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.message.Message;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.MessageNotify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 实时消息推送（V0.1 单机版）。
 *
 * <p>消息先持久化并 ACK，再推送给在线连接；推送失败不影响消息可靠性，
 * 客户端会通过同步游标补偿。跨 Gateway 推送后续由 RabbitMQ 投递消费替代。</p>
 */
@Component
public class MessagePushService {

    private static final Logger log = LoggerFactory.getLogger(MessagePushService.class);

    private final ConnectionRegistry connectionRegistry;

    public MessagePushService(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public void push(Message message, List<Long> recipientUserIds, Connection origin) {
        MessageNotify notify = MessageNotify.newBuilder()
                .setMessageId(message.id())
                .setConversationId(message.conversationId())
                .setSenderId(message.senderId())
                .setSequence(message.seq())
                .setMessageType(message.type().code())
                .setContent(com.google.protobuf.ByteString.copyFrom(message.content()))
                .setCreatedAt(message.createdAt().toEpochMilli())
                .setClientMessageId(message.clientMsgId())
                .build();
        byte[] payload = notify.toByteArray();
        Set<String> pushed = new HashSet<>();
        for (Long recipientUserId : recipientUserIds) {
            pushToUser(recipientUserId, payload, origin, pushed);
        }
        // 发送者其它设备也需要同步该消息（多端同步）。
        pushToUser(message.senderId(), payload, origin, pushed);
    }

    private void pushToUser(long userId, byte[] payload, Connection origin, Set<String> pushed) {
        for (Connection connection : connectionRegistry.findByUserId(userId)) {
            if (connection == origin || !connection.authenticated()) {
                continue;
            }
            if (!pushed.add(connection.connectionId())) {
                continue;
            }
            connection.send(ProtocolMessage.of(Command.MESSAGE_NOTIFY, connection.nextSequence(),
                    null, null, payload));
            log.debug("message pushed: userId={}, connectionId={}", userId, connection.connectionId());
        }
    }
}
