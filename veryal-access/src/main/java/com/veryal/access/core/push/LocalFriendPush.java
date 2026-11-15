package com.veryal.access.core.push;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.application.push.OfflinePushService;
import com.veryal.domain.friend.FriendEvent;
import com.veryal.domain.friend.FriendPushPort;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.FriendNotify;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 好友事件本节点推送：向申请双方在线连接发送 FRIEND_NOTIFY。
 */
@Component
@RequiredArgsConstructor
public class LocalFriendPush implements FriendPushPort {

    private final ConnectionRegistry connectionRegistry;
    private final OfflinePushService offlinePushService;

    @Override
    public void push(FriendEvent event) {
        byte[] payload = FriendNotify.newBuilder()
                .setEventType(event.type().code())
                .setRequestId(event.requestId())
                .setFromUserId(event.fromUserId())
                .setToUserId(event.toUserId())
                .setMessage(event.message() == null ? "" : event.message())
                .setAt(event.at().toEpochMilli())
                .build().toByteArray();
        for (Long userId : event.memberUserIds()) {
            var connections = connectionRegistry.findByUserId(userId);
            if (connections.isEmpty()) {
                offlinePushService.push(userId, event);
                continue;
            }
            for (Connection connection : connections) {
                if (!connection.authenticated()) {
                    continue;
                }
                connection.send(ProtocolMessage.of(Command.FRIEND_NOTIFY,
                        connection.nextSequence(), null, null, payload));
            }
        }
    }
}
