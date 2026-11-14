package com.veryal.protocol.api;

/**
 * 解码后的协议帧：协议头字段 + 未解析的 Protobuf 消息体。
 *
 * <p>消息体使用 byte[] 而非 ByteBuf，换取跨传输层（TCP / WebSocket）复用与更简单的生命周期管理。
 * 后续性能优化阶段可改为池化 ByteBuf。</p>
 */
public record ProtocolFrame(Command command, int protocolVersion, int flags, long sequence, byte[] payload) {

    public ProtocolFrame {
        if (payload == null) {
            payload = new byte[0];
        }
    }
}
