package com.veryal.access.core.push.local;

import com.veryal.access.core.Connection;
import com.veryal.access.core.push.ConnectionPushDedup;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 把 {@link MessageFrame} 写入一条连接：慢连接跳过、按连接去重、文本连接发 JSON 帧。
 *
 * <p>本节点直推与 MQ 网关下发都经过这里，避免两条链路各自维护一份「可写/去重/帧类型」
 * 判断，出现重复推送或帧内容不一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageFrameWriter {

    private final ConnectionPushDedup connectionPushDedup;
    private final AtomicLong unwritableSkips = new AtomicLong();

    /**
     * @return 是否真正写入；连接不可写或该 eventId 已推过时返回 {@code false}
     */
    public boolean write(MessageFrame frame, Connection connection) {
        if (!connection.isWritable()) {
            long total = unwritableSkips.incrementAndGet();
            if (total == 1 || total % 500 == 0) {
                log.warn("skip message push, connection not writable: connectionId={}, skippedTotal={}",
                        connection.connectionId(), total);
            }
            return false;
        }
        if (!connectionPushDedup.firstDelivery(frame.eventId(), connection.connectionId())) {
            return false;
        }
        if (connection.textFrameMode()) {
            connection.sendJson("message", frame.text());
        } else {
            connection.send(ProtocolMessage.of(Command.MESSAGE_NOTIFY, connection.nextSequence(),
                    null, null, frame.protobuf()));
        }
        return true;
    }
}
