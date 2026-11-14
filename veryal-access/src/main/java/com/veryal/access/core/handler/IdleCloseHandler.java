package com.veryal.access.core.handler;

import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.access.core.metrics.GatewayMetrics;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 读空闲关闭：客户端必须依靠心跳保活，超过租约时间未收到数据即断开。
 */
@ChannelHandler.Sharable
@Component
public class IdleCloseHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(IdleCloseHandler.class);

    private final GatewayMetrics metrics;

    public IdleCloseHandler(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleEvent && idleEvent.state() == IdleState.READER_IDLE) {
            Connection connection = ChannelAttributes.connection(ctx.channel());
            metrics.heartbeatTimeout();
            log.info("heartbeat timeout, closing connection: connectionId={}",
                    connection == null ? "unknown" : connection.connectionId());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, event);
    }
}
