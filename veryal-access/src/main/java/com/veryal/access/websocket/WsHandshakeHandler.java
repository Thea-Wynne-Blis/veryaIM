package com.veryal.access.websocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手前置处理：在 HTTP 升级之前把 {@code ticket} / {@code format} 写入 Channel 属性。
 *
 * <p>顺手去掉 query string，因为 {@link io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler}
 * 默认按 path 精确匹配握手地址。</p>
 */
@Component
@ChannelHandler.Sharable
public class WsHandshakeHandler extends ChannelInboundHandlerAdapter {

    static final AttributeKey<String> TICKET = AttributeKey.valueOf("veryal.ws.ticket");
    static final AttributeKey<String> FORMAT = AttributeKey.valueOf("veryal.ws.format");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, List<String>> params = decoder.parameters();
        ctx.channel().attr(TICKET).set(first(params, "ticket"));
        ctx.channel().attr(FORMAT).set(first(params, "format"));
        request.setUri(decoder.path());
        ctx.fireChannelRead(request);
    }

    static boolean textMode(ChannelHandlerContext ctx) {
        return "json".equalsIgnoreCase(ctx.channel().attr(FORMAT).get());
    }

    private String first(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
