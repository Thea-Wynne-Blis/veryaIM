package com.veryal.protocol.netty;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.ProtocolException;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolFrame;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.Envelope;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * 将协议帧解析为业务协议消息，并校验帧头指令与 Envelope 指令的一致性。
 */
public final class EnvelopeDecoder extends MessageToMessageDecoder<ProtocolFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ProtocolFrame frame, List<Object> out) {
        Envelope envelope = EnvelopeCodec.decode(frame.payload());
        Command envelopeCommand = Command.fromCode(envelope.getCommand());
        if (envelopeCommand != frame.command()) {
            throw new ProtocolException(ErrorCode.INVALID_FRAME,
                    "frame command " + frame.command() + " does not match envelope command " + envelopeCommand);
        }
        out.add(EnvelopeCodec.toMessage(envelope, frame.sequence()));
    }
}
