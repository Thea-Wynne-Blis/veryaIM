package com.veryal.protocol.protobuf;

import com.veryal.common.exception.ProtocolException;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeCodecTest {

    @Test
    void roundTripsEnvelopeAndMapsToProtocolMessage() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        Envelope envelope = EnvelopeCodec.envelope(Command.SEND_MESSAGE, "req-1", "trace-1", payload);

        byte[] encoded = EnvelopeCodec.encode(envelope);
        Envelope decoded = EnvelopeCodec.decode(encoded);
        ProtocolMessage message = EnvelopeCodec.toMessage(decoded, 42L);

        assertThat(message.command()).isEqualTo(Command.SEND_MESSAGE);
        assertThat(message.sequence()).isEqualTo(42L);
        assertThat(message.requestId()).isEqualTo("req-1");
        assertThat(message.traceId()).isEqualTo("trace-1");
        assertThat(message.payload()).isEqualTo(payload);
    }

    @Test
    void parsesTypedPayload() {
        LoginRequest loginRequest = LoginRequest.newBuilder()
                .setToken("token")
                .setDeviceId("device-1")
                .setPlatform("WEB")
                .build();
        Envelope envelope = EnvelopeCodec.envelope(Command.LOGIN, "req", "trace", loginRequest.toByteArray());
        LoginRequest parsed = EnvelopeCodec.parsePayload(envelope.getPayload().toByteArray(), LoginRequest.parser());
        assertThat(parsed.getDeviceId()).isEqualTo("device-1");
    }

    @Test
    void rejectsGarbagePayload() {
        assertThatThrownBy(() -> EnvelopeCodec.decode(new byte[]{1, 2, 3, 4, 5}))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void rejectsUnknownCommandCode() {
        Envelope envelope = Envelope.newBuilder()
                .setProtocolVersion(1)
                .setCommand(0x7777)
                .build();
        assertThatThrownBy(() -> EnvelopeCodec.toMessage(envelope, 1L))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void rejectsFutureProtocolVersion() {
        Envelope envelope = Envelope.newBuilder()
                .setProtocolVersion(2)
                .setCommand(Command.HEARTBEAT.code())
                .build();
        assertThatThrownBy(() -> EnvelopeCodec.toMessage(envelope, 1L))
                .isInstanceOf(ProtocolException.class);
    }
}
