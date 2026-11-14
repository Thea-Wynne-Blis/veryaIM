package com.veryal.bootstrap.support;

import com.google.protobuf.MessageLite;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.Envelope;
import com.veryal.protocol.protobuf.EnvelopeCodec;

import java.nio.ByteBuffer;

/**
 * 测试用协议帧编解码，独立于服务端实现，避免“用同一份代码验证自己”。
 */
public final class TestFrames {

    private TestFrames() {
    }

    public static byte[] encode(Command command, long sequence, String requestId, MessageLite body) {
        byte[] payload = EnvelopeCodec.encode(EnvelopeCodec.envelope(command, requestId, "test-trace",
                body.toByteArray()));
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.HEADER_LENGTH + payload.length);
        buffer.putInt(ProtocolConstants.MAGIC);
        buffer.put((byte) ProtocolConstants.PROTOCOL_VERSION);
        buffer.put((byte) ProtocolConstants.FLAG_PROTOBUF);
        buffer.putShort((short) command.code());
        buffer.putInt((int) sequence);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    public static byte[] encode(Command command, MessageLite body) {
        return encode(command, 1L, "req-1", body);
    }

    /**
     * 解析完整协议帧。
     */
    public static Frame decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int magic = buffer.getInt();
        if (magic != ProtocolConstants.MAGIC) {
            throw new IllegalArgumentException("invalid magic: " + Integer.toHexString(magic));
        }
        int version = buffer.get() & 0xFF;
        int flags = buffer.get() & 0xFF;
        int commandCode = buffer.getShort() & 0xFFFF;
        long sequence = Integer.toUnsignedLong(buffer.getInt());
        int bodyLength = buffer.getInt();
        if (bodyLength != buffer.remaining()) {
            throw new IllegalArgumentException("body length mismatch: " + bodyLength + " vs " + buffer.remaining());
        }
        byte[] payload = new byte[bodyLength];
        buffer.get(payload);
        Envelope envelope = EnvelopeCodec.decode(payload);
        ProtocolMessage message = new ProtocolMessage(Command.fromCode(commandCode), version, sequence,
                envelope.getRequestId(), envelope.getTraceId(), envelope.getPayload().toByteArray());
        return new Frame(message, flags);
    }

    public record Frame(ProtocolMessage message, int flags) {
    }
}
