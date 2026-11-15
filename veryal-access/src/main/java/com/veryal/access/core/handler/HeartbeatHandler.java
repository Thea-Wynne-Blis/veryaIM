package com.veryal.access.core.handler;

import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.ProtocolMessages;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.SessionApplicationService;
import com.veryal.common.core.TimeProvider;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.HeartbeatResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 心跳处理：续期本地连接租约与在线状态，不进入业务线程池。
 */
@Slf4j
@ChannelHandler.Sharable
@Component
@RequiredArgsConstructor
public class HeartbeatHandler extends SimpleChannelInboundHandler<ProtocolMessage> {

    private final SessionApplicationService sessionService;
    private final TimeProvider timeProvider;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage message) {
        if (message.command() != Command.HEARTBEAT) {
            ctx.fireChannelRead(message);
            return;
        }
        Connection connection = ChannelAttributes.connection(ctx.channel());
        if (connection == null) {
            ctx.close();
            return;
        }
        Instant now = timeProvider.now();
        connection.heartbeat(now);
        if (connection.authenticated()) {
            try {
                sessionService.heartbeat(new AuthenticatedUser(connection.userId(),
                        connection.deviceId(), connection.tokenId()), connection.connectionId());
            } catch (RuntimeException e) {
                log.warn("heartbeat lease renewal failed: connectionId={}", connection.connectionId(), e);
            }
        }
        connection.send(ProtocolMessages.response(Command.HEARTBEAT_ACK, connection.nextSequence(), message,
                HeartbeatResponse.newBuilder().setServerTime(now.toEpochMilli()).build()));
    }
}
