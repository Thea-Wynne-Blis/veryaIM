package com.veryal.access.core.limit;

import com.veryal.access.core.metrics.GatewayMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 连接准入处理器：在 TLS/协议握手前检查连接数，超限直接关闭。
 */
@Slf4j
@RequiredArgsConstructor
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<Boolean> ACQUIRED =
            AttributeKey.valueOf("veryal.connection.limit.acquired");

    private final ConnectionLimiter connectionLimiter;
    private final GatewayMetrics metrics;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String ip = clientIp(ctx.channel().remoteAddress());
        if (!connectionLimiter.tryAcquire(ip)) {
            metrics.connectionRejected("max-connections");
            log.warn("connection rejected by limiter: ip={}", ip);
            ctx.close();
            return;
        }
        ctx.channel().attr(ACQUIRED).set(Boolean.TRUE);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (Boolean.TRUE.equals(ctx.channel().attr(ACQUIRED).getAndSet(null))) {
            connectionLimiter.release(clientIp(ctx.channel().remoteAddress()));
        }
        super.channelInactive(ctx);
    }

    private String clientIp(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getAddress() == null
                    ? inetSocketAddress.getHostString()
                    : inetSocketAddress.getAddress().getHostAddress();
        }
        return address == null ? "unknown" : address.toString();
    }
}
