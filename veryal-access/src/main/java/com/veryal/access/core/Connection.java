package com.veryal.access.core;

import com.veryal.protocol.api.ProtocolMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一条客户端连接。
 *
 * <p>Channel 只存在于当前 Gateway 本地；Redis 只保存路由元数据。连接对象由 EventLoop 创建，
 * 认证信息在登录成功后写入。</p>
 */
public final class Connection {

    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    private final String connectionId;
    private final String gatewayId;
    private final Channel channel;
    private final Instant connectTime;
    private final AtomicLong sequence = new AtomicLong();

    private volatile long userId;
    private volatile long tenantId;
    private volatile String deviceId;
    private volatile String tokenId;
    private volatile Instant lastHeartbeat;
    private volatile ConnectionStatus status;

    private Connection(String connectionId, String gatewayId, Channel channel, Instant connectTime) {
        this.connectionId = connectionId;
        this.gatewayId = gatewayId;
        this.channel = channel;
        this.connectTime = connectTime;
        this.lastHeartbeat = connectTime;
        this.status = ConnectionStatus.CONNECTED;
    }

    public static Connection create(String connectionId, String gatewayId, Channel channel, Instant now) {
        return new Connection(connectionId, gatewayId, channel, now);
    }

    public void markAuthenticating() {
        this.status = ConnectionStatus.AUTHENTICATING;
    }

    public void markOnline(long userId, long tenantId, String deviceId, String tokenId, Instant now) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.tokenId = tokenId;
        this.lastHeartbeat = now;
        this.status = ConnectionStatus.ONLINE;
    }

    public void markClosed() {
        this.status = ConnectionStatus.CLOSED;
    }

    public boolean authenticated() {
        return status == ConnectionStatus.ONLINE;
    }

    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    public void heartbeat(Instant now) {
        this.lastHeartbeat = now;
    }

    /**
     * 线程安全写入；业务线程与 EventLoop 都可以调用。
     */
    public void send(ProtocolMessage message) {
        if (!channel.isActive()) {
            log.debug("skip send, channel inactive: connectionId={}", connectionId);
            return;
        }
        channel.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.debug("write failed: connectionId={}, command={}", connectionId, message.command(),
                        future.cause());
            }
        });
    }

    public void close() {
        if (channel.isActive()) {
            channel.close();
        }
    }

    public String connectionId() {
        return connectionId;
    }

    public String gatewayId() {
        return gatewayId;
    }

    public Channel channel() {
        return channel;
    }

    public Instant connectTime() {
        return connectTime;
    }

    public long userId() {
        return userId;
    }

    public long tenantId() {
        return tenantId;
    }

    public String deviceId() {
        return deviceId;
    }

    public String tokenId() {
        return tokenId;
    }

    public Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    public ConnectionStatus status() {
        return status;
    }

    @Override
    public String toString() {
        return "Connection{connectionId='" + connectionId + "', userId=" + userId
                + ", deviceId='" + deviceId + "', status=" + status + '}';
    }
}
