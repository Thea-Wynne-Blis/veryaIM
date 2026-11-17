package com.veryal.access.core;

import com.veryal.common.json.JsonUtils;
import com.veryal.protocol.api.ProtocolMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一条客户端连接。
 *
 * <p>Channel 只存在于当前 Gateway 本地；Redis 只保存路由元数据。连接对象由 EventLoop 创建，
 * 认证信息在登录成功后写入。</p>
 */
@Slf4j
public final class Connection {

    /** 服务端主动终止会话（登出、封禁、设备下线）时使用的业务关闭码。 */
    public static final int CLOSE_SESSION_TERMINATED = 4001;

    private final String connectionId;
    private final String gatewayId;
    private final Channel channel;
    private final Instant connectTime;
    private final AtomicLong sequence = new AtomicLong();

    private volatile long userId;
    private volatile String deviceId;
    private volatile String tokenId;
    private volatile Instant lastHeartbeat;
    private volatile ConnectionStatus status;
    /** 浏览器等文本客户端：下行使用 JSON 文本帧，而不是 Protobuf 二进制帧。 */
    private volatile boolean textFrameMode;

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

    public void markOnline(long userId, String deviceId, String tokenId, Instant now) {
        this.userId = userId;
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
        if (textFrameMode) {
            // 文本连接只接受 JSON，不认识的二进制下行直接丢弃，避免 WebSocket 帧类型错乱。
            log.debug("skip binary frame on text connection: connectionId={}, command={}",
                    connectionId, message.command());
            return;
        }
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

    /**
     * 文本客户端下行：统一 {@code {cmd, seq, data, ts}}，seq 单调递增便于客户端断言丢失。
     */
    public void sendJson(String command, Object data) {
        if (!channel.isActive()) {
            log.debug("skip json send, channel inactive: connectionId={}", connectionId);
            return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>(4);
        envelope.put("cmd", command);
        envelope.put("seq", sequence.incrementAndGet());
        envelope.put("data", data);
        envelope.put("ts", System.currentTimeMillis());
        String json;
        try {
            json = JsonUtils.toJson(envelope);
        } catch (RuntimeException e) {
            log.warn("serialize json failed: connectionId={}, cmd={}", connectionId, command, e);
            return;
        }
        channel.writeAndFlush(new TextWebSocketFrame(json))
                .addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        log.debug("json write failed: connectionId={}, cmd={}", connectionId, command,
                                future.cause());
                    }
                });
    }

    public boolean textFrameMode() {
        return textFrameMode;
    }

    public void markTextFrameMode() {
        this.textFrameMode = true;
    }

    public void close() {
        if (channel.isActive()) {
            channel.close();
        }
    }

    /**
     * 会话被服务端终止：先给文本客户端一条提示，再发带业务关闭码的关闭帧。
     *
     * <p>关闭码 {@link #CLOSE_SESSION_TERMINATED} 让客户端区分「网络抖动」与「会话失效」：
     * 前者要自动重连，后者必须停止重连并回到登录页。</p>
     *
     * @return 是否真的发出了关闭动作
     */
    public boolean closeWithReason(int code, String reason) {
        if (!channel.isActive()) {
            return false;
        }
        if (textFrameMode) {
            Map<String, Object> data = new LinkedHashMap<>(2);
            data.put("code", code);
            data.put("reason", reason);
            sendJson("session_closed", data);
        }
        channel.writeAndFlush(new CloseWebSocketFrame(code, reason))
                .addListener(ChannelFutureListener.CLOSE);
        return true;
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

    public long userId() {
        return userId;
    }

    public String deviceId() {
        return deviceId;
    }

    public String tokenId() {
        return tokenId;
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
