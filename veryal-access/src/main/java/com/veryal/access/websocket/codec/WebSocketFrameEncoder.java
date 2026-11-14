package com.veryal.access.websocket.codec;

import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolFrame;
import com.veryal.protocol.netty.VeryalFrameEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.List;

/**
 * 将协议帧编码为 WebSocket Binary Frame，帧格式与 TCP 完全一致。
 */
public final class WebSocketFrameEncoder extends MessageToMessageEncoder<ProtocolFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolFrame frame, List<Object> out) {
        ByteBuf buffer = ctx.alloc().buffer(ProtocolConstants.HEADER_LENGTH + frame.payload().length);
        VeryalFrameEncoder.writeFrame(frame, buffer);
        out.add(new BinaryWebSocketFrame(buffer));
    }
}
