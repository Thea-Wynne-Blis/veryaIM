package com.veryal.access.core.push.local;

import com.google.protobuf.ByteString;
import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.message.event.ReadReceiptEvent;
import com.veryal.domain.message.event.ReadReceiptPushPort;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.ReadNotify;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 已读回执本节点推送：只向本节点上的会话成员连接发送 READ_NOTIFY。
 */
@Component
@RequiredArgsConstructor
public class LocalReadReceiptPush implements ReadReceiptPushPort {

    private final ConnectionRegistry connectionRegistry;

    @Override
    public void push(ReadReceiptEvent event) {
        byte[] payload = ReadNotify.newBuilder()
                .setConversationId(event.conversationId())
                .setReaderId(event.readerId())
                .setReadSequence(event.readSequence())
                .setReadAt(event.readAt().toEpochMilli())
                .build().toByteArray();
        for (Long memberId : event.memberUserIds()) {
            for (Connection connection : connectionRegistry.findByUserId(memberId)) {
                if (!connection.authenticated()) {
                    continue;
                }
                if (memberId == event.readerId() && event.readerDeviceId() != null
                        && event.readerDeviceId().equals(connection.deviceId())) {
                    continue;
                }
                connection.send(ProtocolMessage.of(Command.READ_NOTIFY, connection.nextSequence(),
                        null, null, payload));
            }
        }
    }
}
