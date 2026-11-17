package com.veryal.access.websocket;

import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.access.core.config.GatewayProperties;
import com.veryal.access.core.handler.AuthenticationHandler;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.SessionApplicationService;
import com.veryal.common.core.TimeProvider;
import com.veryal.domain.identity.WsTicketPort;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 握手完成后的票据认证：浏览器建连即完成登录，不再发送 LOGIN 帧。
 *
 * <p>没有 ticket 时直接放行，原生客户端继续走二进制协议里的 LOGIN 流程。</p>
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WsTicketAuthHandler extends ChannelInboundHandlerAdapter {

    private static final int CLOSE_POLICY_VIOLATION = 1008;

    private final WsTicketPort wsTicketPort;
    private final SessionApplicationService sessionService;
    private final com.veryal.access.core.ConnectionRegistry connectionRegistry;
    private final GatewayProperties gatewayProperties;
    private final TimeProvider timeProvider;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (!(event instanceof WebSocketServerProtocolHandler.HandshakeComplete)) {
            super.userEventTriggered(ctx, event);
            return;
        }
        String ticket = ctx.channel().attr(WsHandshakeHandler.TICKET).get();
        if (ticket == null || ticket.isBlank()) {
            super.userEventTriggered(ctx, event);
            return;
        }
        WsTicketPort.TicketPayload payload;
        try {
            payload = wsTicketPort.consume(ticket);
        } catch (RuntimeException e) {
            log.warn("ws ticket consume failed: remote={}", ctx.channel().remoteAddress(), e);
            payload = null;
        }
        Connection connection = ChannelAttributes.connection(ctx.channel());
        if (payload == null || connection == null) {
            log.info("ws ticket rejected: remote={}, connectionId={}",
                    ctx.channel().remoteAddress(),
                    connection == null ? null : connection.connectionId());
            reject(ctx.channel(), "invalid or expired ticket");
            return;
        }
        Instant now = timeProvider.now();
        connection.markOnline(payload.userId(), payload.deviceId(), payload.tokenId(), now);
        // channelActive 时 userId 还是 0，不会进按用户索引；认证完成后必须重新注册。
        connectionRegistry.register(connection);
        if (WsHandshakeHandler.textMode(ctx)) {
            connection.markTextFrameMode();
        }
        sessionService.online(new AuthenticatedUser(payload.userId(), payload.deviceId(),
                payload.tokenId()), gatewayProperties.getId(), connection.connectionId());
        AuthenticationHandler.cancelLoginTimeout(ctx.channel());

        Map<String, Object> ready = new LinkedHashMap<>();
        ready.put("userId", Long.toString(payload.userId()));
        ready.put("deviceId", payload.deviceId());
        ready.put("serverTime", now.toEpochMilli());
        connection.sendJson("ready", ready);
        log.info("ws ticket authenticated: connectionId={}, userId={}, deviceId={}, text={}",
                connection.connectionId(), payload.userId(), payload.deviceId(),
                connection.textFrameMode());
        super.userEventTriggered(ctx, event);
    }

    private void reject(Channel channel, String reason) {
        if (!channel.isActive()) {
            return;
        }
        channel.writeAndFlush(new CloseWebSocketFrame(CLOSE_POLICY_VIOLATION, reason))
                .addListener(future -> channel.close());
    }
}
