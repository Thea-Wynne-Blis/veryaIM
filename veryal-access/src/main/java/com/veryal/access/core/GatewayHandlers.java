package com.veryal.access.core;

import com.veryal.access.core.handler.AuthenticationHandler;
import com.veryal.access.core.handler.CommandDispatcher;
import com.veryal.access.core.handler.GatewayExceptionHandler;
import com.veryal.access.core.handler.HeartbeatHandler;
import com.veryal.access.core.handler.IdleCloseHandler;
import io.netty.channel.ChannelPipeline;
import org.springframework.stereotype.Component;

/**
 * TCP 与 WebSocket 共用的业务 Handler 装配。
 *
 * <p>顺序固定为：认证 → 心跳 → 分发 → 空闲关闭 → 异常出口。传输层只负责编码器差异。</p>
 */
@Component
public class GatewayHandlers {

    private final AuthenticationHandler authenticationHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final CommandDispatcher commandDispatcher;
    private final IdleCloseHandler idleCloseHandler;
    private final GatewayExceptionHandler exceptionHandler;

    public GatewayHandlers(AuthenticationHandler authenticationHandler,
                           HeartbeatHandler heartbeatHandler,
                           CommandDispatcher commandDispatcher,
                           IdleCloseHandler idleCloseHandler,
                           GatewayExceptionHandler exceptionHandler) {
        this.authenticationHandler = authenticationHandler;
        this.heartbeatHandler = heartbeatHandler;
        this.commandDispatcher = commandDispatcher;
        this.idleCloseHandler = idleCloseHandler;
        this.exceptionHandler = exceptionHandler;
    }

    public void addBusinessHandlers(ChannelPipeline pipeline) {
        pipeline.addLast("veryal-auth", authenticationHandler);
        pipeline.addLast("veryal-heartbeat", heartbeatHandler);
        pipeline.addLast("veryal-dispatcher", commandDispatcher);
        pipeline.addLast("veryal-idle-close", idleCloseHandler);
        pipeline.addLast("veryal-exception", exceptionHandler);
    }
}
