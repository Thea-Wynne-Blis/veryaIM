package com.veryal.access.core.push;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.message.event.MessageRecallPushPort;
import com.veryal.domain.message.event.MessageRecalledEvent;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.MessageRecall;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 撤回通知本节点推送：向会话成员在线连接发送 MESSAGE_RECALL。
 */
@Component
@RequiredArgsConstructor
public class LocalMessageRecallPush implements MessageRecallPushPort {

    private final ConnectionRegistry connectionRegistry;

    @Override
    public void push(MessageRecalledEvent event) {
        byte[] payload = MessageRecall.newBuilder()
                .setMessageId(event.messageId())
                .setConversationId(event.conversationId())
                .setSequence(event.seq())
                .setRecalledBy(event.senderId())
                .setRecalledAt(event.recalledAt().toEpochMilli())
                .build().toByteArray();
        for (Long memberId : event.memberUserIds()) {
            for (Connection connection : connectionRegistry.findByUserId(memberId)) {
                if (!connection.authenticated()) {
                    continue;
                }
                if (memberId == event.senderId() && event.senderDeviceId() != null
                        && event.senderDeviceId().equals(connection.deviceId())) {
                    continue;
                }
                connection.send(ProtocolMessage.of(Command.MESSAGE_RECALL, connection.nextSequence(),
                        null, null, payload));
            }
        }
    }
}
