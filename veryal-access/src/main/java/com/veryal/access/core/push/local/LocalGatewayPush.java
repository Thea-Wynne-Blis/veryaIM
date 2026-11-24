package com.veryal.access.core.push.local;

import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.domain.delivery.GatewayPushPort;
import com.veryal.domain.message.event.MessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 connectionId 向本节点连接推送消息。
 *
 * <p>MQ 路由模式下，投递消费者通过网关交换机下发
 * {@link com.veryal.domain.delivery.GatewayPushCommand}，本节点只处理属于自己的
 * connectionId；写入细节（可写判断、去重、JSON/Protobuf 帧）与直推链路共用
 * {@link MessageFrameWriter}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalGatewayPush implements GatewayPushPort {

    private final ConnectionRegistry connectionRegistry;
    private final MessageFrameWriter frameWriter;

    @Override
    public void push(MessageCreatedEvent message, List<String> connectionIds) {
        MessageFrame frame = MessageFrame.of(message);
        for (String connectionId : connectionIds) {
            Connection connection = connectionRegistry.find(connectionId);
            if (connection == null || !connection.authenticated()) {
                log.debug("skip gateway push, connection unavailable: connectionId={}", connectionId);
                continue;
            }
            frameWriter.write(frame, connection);
        }
    }
}
