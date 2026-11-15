package com.veryal.access.core.handler;

import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.access.core.config.GatewayExecutorConfig;
import com.veryal.access.core.metrics.GatewayMetrics;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.access.core.protocol.ProtocolMessages;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;
import com.veryal.common.observability.LogFields;
import com.veryal.common.observability.TraceContext;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 命令分发：EventLoop 只做解码与投递，业务处理全部进入有界业务执行器。
 */
@Slf4j
@ChannelHandler.Sharable
@Component
public class CommandDispatcher extends SimpleChannelInboundHandler<ProtocolMessage> {

    private final Map<Command, CommandHandler> handlers;
    private final Executor businessExecutor;
    private final GatewayMetrics metrics;

    public CommandDispatcher(List<CommandHandler> handlers,
                             @Qualifier(GatewayExecutorConfig.BUSINESS_EXECUTOR) Executor businessExecutor,
                             GatewayMetrics metrics) {
        this.businessExecutor = businessExecutor;
        this.metrics = metrics;
        EnumMap<Command, CommandHandler> map = new EnumMap<>(Command.class);
        for (CommandHandler handler : handlers) {
            CommandHandler previous = map.put(handler.command(), handler);
            if (previous != null) {
                throw new IllegalStateException("duplicate command handler: " + handler.command()
                        + " -> " + previous.getClass().getName() + ", " + handler.getClass().getName());
            }
        }
        this.handlers = Map.copyOf(map);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage message) {
        Connection connection = ChannelAttributes.connection(ctx.channel());
        if (connection == null) {
            ctx.close();
            return;
        }
        metrics.messageReceived(message.command());
        CommandHandler handler = handlers.get(message.command());
        if (handler == null) {
            connection.send(ProtocolMessages.error(connection.nextSequence(), message,
                    ErrorCode.UNSUPPORTED_COMMAND, "no handler for command: " + message.command()));
            return;
        }
        try {
            businessExecutor.execute(() -> dispatch(handler, connection, message));
        } catch (RejectedExecutionException e) {
            log.warn("business executor rejected command: command={}, connectionId={}",
                    message.command(), connection.connectionId());
            connection.send(ProtocolMessages.error(connection.nextSequence(), message,
                    ErrorCode.SERVICE_UNAVAILABLE, "server busy, retry later"));
        }
    }

    private void dispatch(CommandHandler handler, Connection connection, ProtocolMessage message) {
        try (TraceContext.Scope ignored = TraceContext.start(message.traceId())) {
            TraceContext.put(LogFields.USER_ID, Long.toString(connection.userId()));
            TraceContext.put(LogFields.DEVICE_ID, connection.deviceId());
            TraceContext.put(LogFields.CONNECTION_ID, connection.connectionId());
            TraceContext.put(LogFields.GATEWAY_ID, connection.gatewayId());
            TraceContext.put(LogFields.COMMAND, message.command().name());
            handler.handle(new CommandContext(connection, message));
        } catch (VeryalException e) {
            metrics.protocolError(e.errorCode().name());
            log.warn("command failed: command={}, connectionId={}, code={}, reason={}",
                    message.command(), connection.connectionId(), e.errorCode().code(), e.getMessage());
            connection.send(ProtocolMessages.error(connection.nextSequence(), message,
                    e.errorCode(), e.getMessage()));
        } catch (Exception e) {
            metrics.protocolError("internal_error");
            log.error("command failed unexpectedly: command={}, connectionId={}",
                    message.command(), connection.connectionId(), e);
            connection.send(ProtocolMessages.error(connection.nextSequence(), message,
                    ErrorCode.INTERNAL_ERROR, "internal error"));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 供测试与诊断使用：已注册的指令集合。
     */
    public java.util.Set<Command> supportedCommands() {
        return handlers.keySet();
    }
}
