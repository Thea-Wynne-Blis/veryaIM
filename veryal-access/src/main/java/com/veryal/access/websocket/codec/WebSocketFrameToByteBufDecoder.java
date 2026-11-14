package com.veryal.access.websocket.codec;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.ProtocolException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.List;

/**
 * 将 WebSocket Binary Frame 还原为 VERYAL 字节流，复用 TCP 的帧解码器。
 */
public final class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) {
        if (frame instanceof BinaryWebSocketFrame) {
            out.add(frame.content().retain());
            return;
        }
        if (frame instanceof TextWebSocketFrame) {
            throw new ProtocolException(ErrorCode.INVALID_FRAME, "text frames are not supported");
        }
    }
}
