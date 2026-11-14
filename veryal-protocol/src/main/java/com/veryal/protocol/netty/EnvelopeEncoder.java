package com.veryal.protocol.netty;

import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolFrame;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.Envelope;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * 将业务协议消息封装为 Envelope 并生成协议帧，后续由传输层编码器写入网络。
 */
public final class EnvelopeEncoder extends MessageToMessageEncoder<ProtocolMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage message, List<Object> out) {
        Envelope envelope = EnvelopeCodec.toEnvelope(message);
        int version = message.protocolVersion() <= 0 ? ProtocolConstants.PROTOCOL_VERSION : message.protocolVersion();
        out.add(new ProtocolFrame(message.command(), version, ProtocolConstants.FLAG_PROTOBUF,
                message.sequence(), EnvelopeCodec.encode(envelope)));
    }
}
