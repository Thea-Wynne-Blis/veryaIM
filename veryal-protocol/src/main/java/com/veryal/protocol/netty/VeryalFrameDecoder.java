package com.veryal.protocol.netty;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.ProtocolException;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * VERYAL 自定义帧解码器。
 *
 * <p>按固定 16 字节协议头拆包，处理字节流粘包 / 半包。协议帧长度超过 {@code maxFrameLength} 时
 * 抛出 {@link ProtocolException}，由接入层关闭连接。</p>
 */
public final class VeryalFrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;

    public VeryalFrameDecoder() {
        this(ProtocolConstants.DEFAULT_MAX_FRAME_LENGTH);
    }

    public VeryalFrameDecoder(int maxFrameLength) {
        if (maxFrameLength <= ProtocolConstants.HEADER_LENGTH) {
            throw new IllegalArgumentException("maxFrameLength must be greater than header length");
        }
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < ProtocolConstants.HEADER_LENGTH) {
            return;
        }
        in.markReaderIndex();

        int magic = in.readInt();
        if (magic != ProtocolConstants.MAGIC) {
            throw new ProtocolException(ErrorCode.INVALID_FRAME,
                    "invalid magic: 0x" + Integer.toHexString(magic));
        }
        int protocolVersion = in.readUnsignedByte();
        if (protocolVersion > ProtocolConstants.PROTOCOL_VERSION) {
            throw new ProtocolException(ErrorCode.PROTOCOL_ERROR,
                    "unsupported protocol version: " + protocolVersion);
        }
        int flags = in.readUnsignedByte();
        int commandCode = in.readUnsignedShort();
        long sequence = in.readUnsignedInt();
        int bodyLength = in.readInt();

        if (bodyLength < 0 || bodyLength > maxFrameLength) {
            throw new ProtocolException(ErrorCode.FRAME_TOO_LARGE,
                    "invalid body length: " + bodyLength + ", max=" + maxFrameLength);
        }
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[bodyLength];
        in.readBytes(body);
        Command command = Command.fromCode(commandCode);
        out.add(new ProtocolFrame(command, protocolVersion, flags, sequence, body));
    }
}
