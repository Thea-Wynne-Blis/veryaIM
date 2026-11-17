package com.veryal.access.websocket;

import com.veryal.access.core.GatewayHandlers;
import com.veryal.access.core.config.GatewayProperties;
import com.veryal.access.core.limit.ConnectionLimitHandler;
import com.veryal.access.core.limit.ConnectionLimiter;
import com.veryal.access.core.metrics.GatewayMetrics;
import com.veryal.access.core.tls.GatewaySslContextFactory;
import com.veryal.access.websocket.codec.WebSocketFrameEncoder;
import com.veryal.access.websocket.codec.WebSocketFrameToByteBufDecoder;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
import com.veryal.protocol.netty.EnvelopeDecoder;
import com.veryal.protocol.netty.EnvelopeEncoder;
import com.veryal.protocol.netty.VeryalFrameDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty WebSocket Gateway。浏览器走 JSON 文本帧，原生客户端走 Protobuf 二进制帧，共用业务 Handler。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "veryal.gateway.websocket", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class WebSocketGatewayServer implements SmartLifecycle {

    private final WebSocketGatewayProperties properties;
    private final GatewayProperties gatewayProperties;
    private final GatewayHandlers gatewayHandlers;
    private final GatewaySslContextFactory sslContextFactory;
    private final ConnectionLimiter connectionLimiter;
    private final GatewayMetrics metrics;
    private final WsHandshakeHandler wsHandshakeHandler;
    private final WsTicketAuthHandler wsTicketAuthHandler;
    private final WebSocketTextHandler webSocketTextHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile int boundPort = -1;
    private volatile boolean running;

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(properties.getBossThreads(), namedFactory("veryal-ws-boss-"));
        workerGroup = new NioEventLoopGroup(properties.getWorkerThreads(), namedFactory("veryal-ws-worker-"));
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, properties.getBacklog())
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 64 * 1024))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast("connection-limit",
                                new ConnectionLimitHandler(connectionLimiter, metrics));
                        SslHandler sslHandler = sslContextFactory.newHandler(channel.alloc());
                        if (sslHandler != null) {
                            pipeline.addLast("tls", sslHandler);
                        }
                        pipeline.addLast("http-codec", new HttpServerCodec());
                        pipeline.addLast("http-aggregator", new HttpObjectAggregator(properties.getMaxHttpContentLength()));
                        pipeline.addLast("ws-handshake", wsHandshakeHandler);
                        pipeline.addLast("ws-protocol", new WebSocketServerProtocolHandler(properties.getPath(), null,
                                true, gatewayProperties.getMaxFrameLength()));
                        pipeline.addLast("ws-ticket-auth", wsTicketAuthHandler);
                        pipeline.addLast("ws-frame-aggregator",
                                new WebSocketFrameAggregator(gatewayProperties.getMaxFrameLength()));
                        pipeline.addLast("veryal-idle",
                                new IdleStateHandler((int) gatewayProperties.getHeartbeatIdle().toSeconds(), 0, 0));
                        pipeline.addLast("ws-text", webSocketTextHandler);
                        pipeline.addLast("ws-to-bytebuf", new WebSocketFrameToByteBufDecoder());
                        pipeline.addLast("veryal-frame-decoder",
                                new VeryalFrameDecoder(gatewayProperties.getMaxFrameLength()));
                        pipeline.addLast("veryal-envelope-decoder", new EnvelopeDecoder());
                        pipeline.addLast("veryal-ws-frame-encoder", new WebSocketFrameEncoder());
                        pipeline.addLast("veryal-envelope-encoder", new EnvelopeEncoder());
                        gatewayHandlers.addBusinessHandlers(pipeline);
                    }
                });
        try {
            serverChannel = bootstrap.bind(properties.getPort()).syncUninterruptibly().channel();
        } catch (RuntimeException e) {
            shutdownGroups();
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "failed to bind WebSocket gateway port " + properties.getPort(), e);
        }
        boundPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        running = true;
        log.info("WebSocket gateway started: gatewayId={}, port={}, path={}",
                gatewayProperties.getId(), boundPort, properties.getPath());
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        shutdownGroups();
        log.info("WebSocket gateway stopped");
    }

    private void shutdownGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            workerGroup = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    public int boundPort() {
        return boundPort;
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }
}
