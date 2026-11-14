package com.veryal.protocol.api;

/**
 * 解析后的业务协议消息：帧头 + Envelope 元数据 + 业务 payload。
 */
public record ProtocolMessage(Command command, int protocolVersion, long sequence,
                             String requestId, String traceId, byte[] payload) {

    public ProtocolMessage {
        if (payload == null) {
            payload = new byte[0];
        }
    }

    public static ProtocolMessage of(Command command, long sequence, String requestId,
                                     String traceId, byte[] payload) {
        return new ProtocolMessage(command, ProtocolConstants.PROTOCOL_VERSION, sequence, requestId, traceId, payload);
    }
}
