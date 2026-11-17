package com.veryal.access.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.access.core.ChannelAttributes;
import com.veryal.access.core.Connection;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.json.JsonUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 浏览器文本协议：{@code {"cmd":"ping","seq":1}}。
 *
 * <p>只处理上行业务之外的控制帧；二进制帧原样放行给 Protobuf 解码链路，
 * 因此同一端口可同时服务浏览器（JSON）与原生客户端（二进制）。</p>
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final TimeProvider timeProvider;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        Connection connection = ChannelAttributes.connection(ctx.channel());
        if (connection == null) {
            ctx.close();
            return;
        }
        if (!connection.textFrameMode()) {
            connection.sendJson("error", Map.of("reason", "text frames require ticket authentication"));
            ctx.close();
            return;
        }
        JsonNode request;
        try {
            request = JsonUtils.mapper().readTree(frame.text());
        } catch (Exception e) {
            connection.sendJson("error", Map.of("reason", "invalid json"));
            return;
        }
        String cmd = request == null ? null : request.path("cmd").asText(null);
        if (cmd == null || cmd.isBlank()) {
            connection.sendJson("error", Map.of("reason", "cmd is required"));
            return;
        }
        switch (cmd) {
            case "ping" -> {
                connection.heartbeat(timeProvider.now());
                connection.sendJson("pong", Map.of("ts", timeProvider.now().toEpochMilli()));
            }
            case "ack" -> {
                // 浏览器端已读/送达仍走 HTTP，这里仅保持连接活跃。
                connection.heartbeat(timeProvider.now());
            }
            default -> connection.sendJson("error", Map.of("reason", "unknown cmd: " + cmd));
        }
    }
}
