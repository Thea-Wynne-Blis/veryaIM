package com.veryal.access.core.push;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.moment.MomentEvent;
import com.veryal.domain.moment.MomentPushPort;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.MomentNotify;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 朋友圈互动本节点推送：向接收人的在线连接发送 MOMENT_NOTIFY。
 *
 * <p>Web 客户端使用 JSON 文本帧，与消息推送保持一致（见 {@code RealtimeDelivery}）：
 * 文本连接发送 {@code moment} 帧，原生客户端仍收 Protobuf 二进制帧。</p>
 */
@Component
@RequiredArgsConstructor
public class LocalMomentPush implements MomentPushPort {

    private final ConnectionRegistry connectionRegistry;

    @Override
    public void push(MomentEvent event) {
        byte[] payload = MomentNotify.newBuilder()
                .setEventType(event.type().code())
                .setMomentId(event.momentId())
                .setActorId(event.actorId())
                .setCommentId(event.commentId() == null ? 0 : event.commentId())
                .setAt(event.at().toEpochMilli())
                .build().toByteArray();
        Map<String, Object> textNotice = textNotice(event);
        for (Long userId : event.recipientIds()) {
            for (Connection connection : connectionRegistry.findByUserId(userId)) {
                if (!connection.authenticated()) {
                    continue;
                }
                if (connection.textFrameMode()) {
                    connection.sendJson("moment", textNotice);
                    continue;
                }
                connection.send(ProtocolMessage.of(Command.MOMENT_NOTIFY,
                        connection.nextSequence(), null, null, payload));
            }
        }
    }

    /**
     * 文本帧里的 ID 一律用字符串：19 位雪花号超过 JS 安全整数，
     * 前端 {@code JSON.parse} 会丢精度，导致跳转详情时 ID 对不上。
     */
    private Map<String, Object> textNotice(MomentEvent event) {
        Map<String, Object> data = new LinkedHashMap<>(5);
        data.put("eventType", event.type().code());
        data.put("momentId", Long.toString(event.momentId()));
        data.put("actorId", Long.toString(event.actorId()));
        data.put("commentId", event.commentId() == null ? null
                : Long.toString(event.commentId()));
        data.put("at", event.at().toEpochMilli());
        return data;
    }
}
