package com.veryal.access.tcp;

import com.veryal.access.core.GatewayHandlers;
import com.veryal.access.core.config.GatewayProperties;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
import com.veryal.protocol.netty.EnvelopeDecoder;
import com.veryal.protocol.netty.EnvelopeEncoder;
import com.veryal.protocol.netty.VeryalFrameDecoder;
import com.veryal.protocol.netty.VeryalFrameEncoder;
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
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty TCP Gateway。
 *
 * <p>Pipeline：Idle → FrameDecoder → EnvelopeDecoder → FrameEncoder → EnvelopeEncoder → 业务 Handler。</p>
 */
@Component
@ConditionalOnProperty(prefix = "veryal.gateway.tcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TcpGatewayServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpGatewayServer.class);

    private final TcpGatewayProperties properties;
    private final GatewayProperties gatewayProperties;
    private final GatewayHandlers gatewayHandlers;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile int boundPort = -1;
    private volatile boolean running;

    public TcpGatewayServer(TcpGatewayProperties properties, GatewayProperties gatewayProperties,
                            GatewayHandlers gatewayHandlers) {
        this.properties = properties;
        this.gatewayProperties = gatewayProperties;
        this.gatewayHandlers = gatewayHandlers;
    }

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(properties.getBossThreads(), namedFactory("veryal-tcp-boss-"));
        workerGroup = new NioEventLoopGroup(properties.getWorkerThreads(), namedFactory("veryal-tcp-worker-"));
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
                        pipeline.addLast("veryal-idle",
                                new IdleStateHandler((int) gatewayProperties.getHeartbeatIdle().toSeconds(), 0, 0));
                        pipeline.addLast("veryal-frame-decoder",
                                new VeryalFrameDecoder(gatewayProperties.getMaxFrameLength()));
                        pipeline.addLast("veryal-envelope-decoder", new EnvelopeDecoder());
                        pipeline.addLast("veryal-frame-encoder", new VeryalFrameEncoder());
                        pipeline.addLast("veryal-envelope-encoder", new EnvelopeEncoder());
                        gatewayHandlers.addBusinessHandlers(pipeline);
                    }
                });
        try {
            serverChannel = bootstrap.bind(properties.getPort()).syncUninterruptibly().channel();
        } catch (RuntimeException e) {
            shutdownGroups();
            throw new SystemException(ErrorCode.CONFIGURATION_ERROR,
                    "failed to bind TCP gateway port " + properties.getPort(), e);
        }
        boundPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        running = true;
        log.info("TCP gateway started: gatewayId={}, port={}", gatewayProperties.getId(), boundPort);
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        shutdownGroups();
        log.info("TCP gateway stopped");
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

    /**
     * 实际监听端口（配置为 0 时由系统分配）。
     */
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
