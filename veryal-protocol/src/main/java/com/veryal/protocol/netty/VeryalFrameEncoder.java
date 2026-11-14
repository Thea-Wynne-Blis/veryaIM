package com.veryal.protocol.netty;

import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * VERYAL 自定义帧编码器（TCP 使用）。
 */
public final class VeryalFrameEncoder extends MessageToByteEncoder<ProtocolFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolFrame frame, ByteBuf out) {
        writeFrame(frame, out);
    }

    /**
     * 将协议帧写入 ByteBuf，WebSocket 编码器复用同一格式。
     */
    public static void writeFrame(ProtocolFrame frame, ByteBuf out) {
        byte[] payload = frame.payload();
        int version = frame.protocolVersion() <= 0 ? ProtocolConstants.PROTOCOL_VERSION : frame.protocolVersion();
        out.writeInt(ProtocolConstants.MAGIC);
        out.writeByte(version);
        out.writeByte(frame.flags());
        out.writeShort(frame.command().code());
        out.writeInt((int) frame.sequence());
        out.writeInt(payload.length);
        out.writeBytes(payload);
    }
}
