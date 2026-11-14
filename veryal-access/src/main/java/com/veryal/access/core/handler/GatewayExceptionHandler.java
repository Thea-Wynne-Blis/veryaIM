package com.veryal.access.core.handler;

import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.access.core.metrics.GatewayMetrics;
import com.veryal.access.core.protocol.ProtocolMessages;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.ProtocolException;
import com.veryal.common.exception.VeryalException;
import com.veryal.protocol.api.ProtocolMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 统一异常出口：协议错误必须断开连接，业务错误只回错误帧并保持连接。
 */
@ChannelHandler.Sharable
@Component
public class GatewayExceptionHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final GatewayMetrics metrics;

    public GatewayExceptionHandler(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Connection connection = ChannelAttributes.connection(ctx.channel());
        String connectionId = connection == null ? "unknown" : connection.connectionId();
        if (cause instanceof ProtocolException protocolException) {
            metrics.protocolError(protocolException.errorCode().name());
            log.warn("protocol error, closing connection: connectionId={}, reason={}",
                    connectionId, protocolException.getMessage());
            safeSend(connection, protocolException.errorCode(), protocolException.getMessage());
            ctx.close();
            return;
        }
        if (cause instanceof VeryalException veryalException) {
            metrics.protocolError(veryalException.errorCode().name());
            log.warn("business error: connectionId={}, code={}, reason={}", connectionId,
                    veryalException.errorCode().code(), veryalException.getMessage());
            safeSend(connection, veryalException.errorCode(), veryalException.getMessage());
            return;
        }
        metrics.protocolError("unexpected");
        log.error("unexpected channel error: connectionId={}", connectionId, cause);
        safeSend(connection, ErrorCode.INTERNAL_ERROR, "internal error");
        ctx.close();
    }

    private void safeSend(Connection connection, ErrorCode code, String message) {
        if (connection == null) {
            return;
        }
        try {
            connection.send(ProtocolMessages.error(connection.nextSequence(), null, code, message));
        } catch (RuntimeException e) {
            log.debug("failed to send error frame: connectionId={}", connection.connectionId(), e);
        }
    }
}
