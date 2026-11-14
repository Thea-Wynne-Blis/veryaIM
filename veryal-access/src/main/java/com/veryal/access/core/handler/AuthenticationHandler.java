package com.veryal.access.core.handler;

import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.access.core.ConnectionRegistry;
import com.veryal.access.core.config.GatewayProperties;
import com.veryal.access.core.metrics.GatewayMetrics;
import com.veryal.access.core.protocol.ProtocolMessages;
import com.veryal.application.identity.AuthApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.SessionApplicationService;
import com.veryal.application.identity.UserApplicationService;
import com.veryal.application.identity.UserProfile;
import com.veryal.common.core.RandomIds;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.VeryalException;
import com.veryal.common.observability.LogFields;
import com.veryal.common.observability.TraceContext;
import com.veryal.common.ratelimit.RateLimitResult;
import com.veryal.common.ratelimit.RateLimiterRegistry;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import com.veryal.protocol.protobuf.LoginRequest;
import com.veryal.protocol.protobuf.LoginResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 认证入口：连接建立后必须先在限定时间内完成 LOGIN，否则断开。
 *
 * <p>日志中的 traceId 来自 Envelope，用户标识在登录成功后写入 MDC。</p>
 */
@Slf4j
@ChannelHandler.Sharable
@Component
@RequiredArgsConstructor
public class AuthenticationHandler extends SimpleChannelInboundHandler<ProtocolMessage> {

    private static final AttributeKey<ScheduledFuture<?>> LOGIN_TIMEOUT =
            AttributeKey.valueOf("veryal.loginTimeout");

    private final AuthApplicationService authService;
    private final UserApplicationService userService;
    private final SessionApplicationService sessionService;
    private final ConnectionRegistry connectionRegistry;
    private final GatewayProperties properties;
    private final GatewayMetrics metrics;
    private final TimeProvider timeProvider;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Connection connection = Connection.create(RandomIds.newId(), properties.getId(),
                ctx.channel(), timeProvider.now());
        ctx.channel().attr(ChannelAttributes.CONNECTION).set(connection);
        connectionRegistry.register(connection);
        metrics.connectionOpened();

        ScheduledFuture<?> timeout = ctx.executor().schedule(() -> {
            if (!connection.authenticated()) {
                log.info("login timeout, closing connection: connectionId={}", connection.connectionId());
                ctx.close();
            }
        }, properties.getLoginTimeout().toMillis(), TimeUnit.MILLISECONDS);
        ctx.channel().attr(LOGIN_TIMEOUT).set(timeout);

        log.debug("connection established: connectionId={}, remote={}", connection.connectionId(),
                ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage message) {
        Connection connection = ChannelAttributes.connection(ctx.channel());
        if (connection == null) {
            ctx.close();
            return;
        }
        if (message.command() == Command.LOGIN) {
            handleLogin(ctx, connection, message);
            return;
        }
        if (!connection.authenticated()) {
            connection.send(ProtocolMessages.error(connection.nextSequence(), message,
                    ErrorCode.UNAUTHORIZED, "login required"));
            ctx.close();
            return;
        }
        ctx.fireChannelRead(message);
    }

    private void handleLogin(ChannelHandlerContext ctx, Connection connection, ProtocolMessage message) {
        try (TraceContext.Scope ignored = TraceContext.start(message.traceId())) {
            connection.markAuthenticating();
            String clientIp = clientIp(ctx);
            RateLimitResult loginRate = rateLimiterRegistry.tryAcquire("tcp-login", clientIp);
            if (!loginRate.allowed()) {
                throw new AuthenticationException(ErrorCode.RATE_LIMITED,
                        "too many login attempts, retry after " + loginRate.retryAfterSeconds() + "s");
            }
            LoginRequest request = EnvelopeCodec.parsePayload(message.payload(), LoginRequest.parser());
            AuthenticatedUser user = authService.authenticate(request.getToken());
            String requestedDeviceId = request.getDeviceId().trim();
            if (!requestedDeviceId.isEmpty() && !requestedDeviceId.equals(user.deviceId())) {
                throw new AuthenticationException(ErrorCode.TOKEN_INVALID,
                        "token device does not match login device");
            }

            Instant now = timeProvider.now();
            connection.markOnline(user.userId(), user.tenantId(), user.deviceId(), user.tokenId(), now);
            connectionRegistry.register(connection);
            sessionService.online(user, properties.getId(), connection.connectionId());
            cancelLoginTimeout(ctx);

            TraceContext.put(LogFields.USER_ID, Long.toString(user.userId()));
            TraceContext.put(LogFields.DEVICE_ID, user.deviceId());
            TraceContext.put(LogFields.CONNECTION_ID, connection.connectionId());
            TraceContext.put(LogFields.GATEWAY_ID, properties.getId());

            UserProfile profile = userService.getProfile(user.userId());
            LoginResponse response = LoginResponse.newBuilder()
                    .setCode(ErrorCode.SUCCESS.code())
                    .setMessage("ok")
                    .setUserId(user.userId())
                    .setNickname(profile.nickname() == null ? "" : profile.nickname())
                    .setDeviceId(user.deviceId())
                    .setServerTime(now.toEpochMilli())
                    .build();
            connection.send(ProtocolMessages.response(Command.LOGIN_RESPONSE,
                    connection.nextSequence(), message, response));
            log.info("connection authenticated: connectionId={}, userId={}, deviceId={}",
                    connection.connectionId(), user.userId(), user.deviceId());
        } catch (VeryalException e) {
            metrics.protocolError("auth_failed");
            log.warn("login failed: connectionId={}, code={}, reason={}", connection.connectionId(),
                    e.errorCode().code(), e.getMessage());
            connection.send(ProtocolMessages.error(connection.nextSequence(), message,
                    e.errorCode(), e.getMessage()));
            connection.channel().writeAndFlush(io.netty.buffer.Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelLoginTimeout(ctx);
        Connection connection = ChannelAttributes.connection(ctx.channel());
        if (connection != null) {
            if (connection.authenticated()) {
                try {
                    sessionService.offline(new AuthenticatedUser(connection.userId(), connection.tenantId(),
                            connection.deviceId(), connection.tokenId()), connection.connectionId());
                } catch (RuntimeException e) {
                    log.warn("failed to mark offline: connectionId={}", connection.connectionId(), e);
                }
            }
            connectionRegistry.unregister(connection.connectionId());
            metrics.connectionClosed();
            log.debug("connection closed: connectionId={}, userId={}", connection.connectionId(),
                    connection.userId());
        }
        super.channelInactive(ctx);
    }

    private void cancelLoginTimeout(ChannelHandlerContext ctx) {
        ScheduledFuture<?> future = ctx.channel().attr(LOGIN_TIMEOUT).getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private String clientIp(ChannelHandlerContext ctx) {
        SocketAddress address = ctx.channel().remoteAddress();
        if (address instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getAddress() == null
                    ? inetSocketAddress.getHostString()
                    : inetSocketAddress.getAddress().getHostAddress();
        }
        return address == null ? "unknown" : address.toString();
    }
}
