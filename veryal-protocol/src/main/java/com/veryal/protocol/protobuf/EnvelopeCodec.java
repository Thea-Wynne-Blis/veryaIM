package com.veryal.protocol.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.ProtocolException;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolMessage;

/**
 * Envelope 编解码。
 *
 * <p>帧头中的 command 与 Envelope.command 必须一致；Envelope 是业务消息的通用元数据容器。</p>
 */
public final class EnvelopeCodec {

    private EnvelopeCodec() {
    }

    public static Envelope decode(byte[] payload) {
        try {
            return Envelope.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtocolException(ErrorCode.PROTOCOL_ERROR, "invalid protobuf envelope", e);
        }
    }

    public static byte[] encode(Envelope envelope) {
        return envelope.toByteArray();
    }

    public static Envelope envelope(Command command, String requestId, String traceId, byte[] payload) {
        return Envelope.newBuilder()
                .setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION)
                .setCommand(command.code())
                .setRequestId(nullToEmpty(requestId))
                .setTraceId(nullToEmpty(traceId))
                .setPayload(ByteString.copyFrom(payload == null ? new byte[0] : payload))
                .build();
    }

    /**
     * 将 Envelope 转换为与传输无关的协议消息，并校验版本与指令一致性。
     */
    public static ProtocolMessage toMessage(Envelope envelope, long sequence) {
        int version = envelope.getProtocolVersion();
        if (version <= 0) {
            version = ProtocolConstants.PROTOCOL_VERSION;
        }
        if (version > ProtocolConstants.PROTOCOL_VERSION) {
            throw new ProtocolException(ErrorCode.PROTOCOL_ERROR,
                    "unsupported protocol version: " + version);
        }
        Command command = Command.fromCode(envelope.getCommand());
        return new ProtocolMessage(command, version, sequence, envelope.getRequestId(),
                envelope.getTraceId(), envelope.getPayload().toByteArray());
    }

    public static Envelope toEnvelope(ProtocolMessage message) {
        return envelope(message.command(), message.requestId(), message.traceId(), message.payload());
    }

    public static <T extends MessageLite> T parsePayload(byte[] payload, Parser<T> parser) {
        try {
            return parser.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtocolException(ErrorCode.PROTOCOL_ERROR, "invalid protobuf payload", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
