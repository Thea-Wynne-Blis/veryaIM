package com.veryal.protocol.netty;

import com.veryal.common.exception.ProtocolException;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolFrame;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameCodecTest {

    private static ProtocolFrame heartbeatFrame(String body) {
        byte[] payload = EnvelopeCodec.encode(EnvelopeCodec.envelope(Command.HEARTBEAT, "req", "trace",
                body.getBytes(StandardCharsets.UTF_8)));
        return new ProtocolFrame(Command.HEARTBEAT, ProtocolConstants.PROTOCOL_VERSION,
                ProtocolConstants.FLAG_PROTOBUF, 7L, payload);
    }

    @Test
    void encoderWritesDocumentedHeader() {
        EmbeddedChannel channel = new EmbeddedChannel(new VeryalFrameEncoder());
        ProtocolFrame frame = heartbeatFrame("ping");
        assertThat(channel.writeOutbound(frame)).isTrue();

        ByteBuf encoded = channel.readOutbound();
        byte[] bytes = ByteBufUtil.getBytes(encoded);
        encoded.release();

        assertThat(bytes.length).isEqualTo(ProtocolConstants.HEADER_LENGTH + frame.payload().length);
        ByteBuf header = Unpooled.wrappedBuffer(bytes);
        assertThat(header.readInt()).isEqualTo(ProtocolConstants.MAGIC);
        assertThat((int) header.readUnsignedByte()).isEqualTo(ProtocolConstants.PROTOCOL_VERSION);
        assertThat((int) header.readUnsignedByte()).isEqualTo(ProtocolConstants.FLAG_PROTOBUF);
        assertThat(header.readUnsignedShort()).isEqualTo(Command.HEARTBEAT.code());
        assertThat(header.readUnsignedInt()).isEqualTo(7L);
        assertThat(header.readInt()).isEqualTo(frame.payload().length);
    }

    @Test
    void decoderHandlesFragmentedFrame() {
        EmbeddedChannel encoder = new EmbeddedChannel(new VeryalFrameEncoder());
        encoder.writeOutbound(heartbeatFrame("ping"));
        ByteBuf encoded = encoder.readOutbound();
        byte[] bytes = ByteBufUtil.getBytes(encoded);
        encoded.release();

        EmbeddedChannel decoder = new EmbeddedChannel(new VeryalFrameDecoder(1024 * 1024));
        assertThat(decoder.writeInbound(Unpooled.copiedBuffer(bytes, 0, 5))).isFalse();
        assertThat(decoder.writeInbound(Unpooled.copiedBuffer(bytes, 5, bytes.length - 5))).isTrue();

        ProtocolFrame decoded = decoder.readInbound();
        assertThat(decoded).isNotNull();
        assertThat(decoded.command()).isEqualTo(Command.HEARTBEAT);
        assertThat(decoded.sequence()).isEqualTo(7L);
        assertThat(decoded.payload()).isEqualTo(heartbeatFrame("ping").payload());
    }

    @Test
    void decoderHandlesMultipleFramesInOneBuffer() {
        EmbeddedChannel encoder = new EmbeddedChannel(new VeryalFrameEncoder());
        encoder.writeOutbound(heartbeatFrame("one"));
        encoder.writeOutbound(heartbeatFrame("two"));
        ByteBuf first = encoder.readOutbound();
        ByteBuf second = encoder.readOutbound();

        EmbeddedChannel decoder = new EmbeddedChannel(new VeryalFrameDecoder(1024 * 1024));
        assertThat(decoder.writeInbound(Unpooled.wrappedBuffer(first, second))).isTrue();
        assertThat((ProtocolFrame) decoder.readInbound()).isNotNull();
        assertThat((ProtocolFrame) decoder.readInbound()).isNotNull();
        assertThat((Object) decoder.readInbound()).isNull();
    }

    @Test
    void decoderRejectsInvalidMagic() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(0x00000000);
        buffer.writeByte(1).writeByte(0).writeShort(Command.HEARTBEAT.code()).writeInt(1).writeInt(0);

        EmbeddedChannel decoder = new EmbeddedChannel(new VeryalFrameDecoder(1024));
        assertThatThrownBy(() -> decoder.writeInbound(buffer))
                .isInstanceOf(DecoderException.class)
                .hasCauseInstanceOf(ProtocolException.class);
        buffer.release();
    }

    @Test
    void decoderRejectsOversizedBody() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(ProtocolConstants.MAGIC);
        buffer.writeByte(1).writeByte(0).writeShort(Command.HEARTBEAT.code()).writeInt(1);
        buffer.writeInt(2048);

        EmbeddedChannel decoder = new EmbeddedChannel(new VeryalFrameDecoder(1024));
        assertThatThrownBy(() -> decoder.writeInbound(buffer))
                .isInstanceOf(DecoderException.class)
                .hasCauseInstanceOf(ProtocolException.class);
    }

    @Test
    void envelopeDecoderMapsFrameToProtocolMessage() {
        EmbeddedChannel decoder = new EmbeddedChannel(new EnvelopeDecoder());
        EnvelopeCodecTestSupport support = new EnvelopeCodecTestSupport();
        ProtocolFrame frame = support.sendMessageFrame();

        assertThat(decoder.writeInbound(frame)).isTrue();
        ProtocolMessage message = decoder.readInbound();
        assertThat(message).isNotNull();
        assertThat(message.command()).isEqualTo(Command.SEND_MESSAGE);
        assertThat(message.requestId()).isEqualTo("req-1");
    }

    @Test
    void envelopeDecoderRejectsMismatchedCommand() {
        EmbeddedChannel decoder = new EmbeddedChannel(new EnvelopeDecoder());
        byte[] payload = EnvelopeCodec.encode(EnvelopeCodec.envelope(Command.HEARTBEAT, "req", "trace",
                new byte[]{1}));
        ProtocolFrame frame = new ProtocolFrame(Command.SEND_MESSAGE, 1, 1, 1, payload);

        assertThatThrownBy(() -> decoder.writeInbound(frame))
                .isInstanceOf(DecoderException.class)
                .hasCauseInstanceOf(ProtocolException.class);
    }

    private static final class EnvelopeCodecTestSupport {

        ProtocolFrame sendMessageFrame() {
            byte[] payload = EnvelopeCodec.encode(EnvelopeCodec.envelope(Command.SEND_MESSAGE, "req-1", "trace-1",
                    "payload".getBytes(StandardCharsets.UTF_8)));
            return new ProtocolFrame(Command.SEND_MESSAGE, ProtocolConstants.PROTOCOL_VERSION,
                    ProtocolConstants.FLAG_PROTOBUF, 1L, payload);
        }
    }
}
