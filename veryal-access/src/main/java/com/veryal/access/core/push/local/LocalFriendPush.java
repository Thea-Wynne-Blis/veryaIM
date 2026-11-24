package com.veryal.access.core.push.local;

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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 好友事件本节点推送：向申请双方在线连接发送 FRIEND_NOTIFY。
 *
 * <p>与消息、朋友圈推送保持一致：Web 客户端使用 JSON 文本帧，
 * 原生客户端仍收 Protobuf 二进制帧。此前这里只发二进制帧，
 * 文本连接会被 {@link Connection#send} 直接丢弃，导致浏览器端
 * 收不到好友事件、必须刷新才能看到新会话。</p>
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
        Map<String, Object> textNotice = textNotice(event);
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
                if (connection.textFrameMode()) {
                    connection.sendJson("friend", textNotice);
                    continue;
                }
                connection.send(ProtocolMessage.of(Command.FRIEND_NOTIFY,
                        connection.nextSequence(), null, null, payload));
            }
        }
    }

    /** 文本帧里的 ID 一律用字符串，避免 19 位雪花号在 JS 里丢精度。 */
    private Map<String, Object> textNotice(FriendEvent event) {
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("eventType", event.type().code());
        data.put("requestId", Long.toString(event.requestId()));
        data.put("fromUserId", Long.toString(event.fromUserId()));
        data.put("toUserId", Long.toString(event.toUserId()));
        data.put("message", event.message() == null ? "" : event.message());
        data.put("at", event.at().toEpochMilli());
        return data;
    }
}
