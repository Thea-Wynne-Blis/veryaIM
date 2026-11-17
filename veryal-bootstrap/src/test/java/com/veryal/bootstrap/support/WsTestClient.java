package com.veryal.bootstrap.support;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolFrame;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.netty.VeryalFrameEncoder;
import com.veryal.protocol.protobuf.EnvelopeCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 测试客户端（二进制协议帧）：与原生客户端走同一条链路。
 *
 * <p>下行帧由 {@link com.veryal.access.websocket.codec.WebSocketFrameEncoder} 包装，
 * 字节内容与帧协议完全一致，因此这里复用协议层的帧格式解析。</p>
 */
public final class WsTestClient implements AutoCloseable {

    private final WebSocket socket;
    private final BlockingQueue<byte[]> frames;
    private final AtomicLong sequence = new AtomicLong();

    private WsTestClient(WebSocket socket, BlockingQueue<byte[]> frames) {
        this.socket = socket;
        this.frames = frames;
    }

    /** 建立不带票据的连接，走二进制 LOGIN 认证（原生客户端路径）。 */
    public static WsTestClient connect(int port) throws Exception {
        BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), new Collector(frames))
                .get(10, TimeUnit.SECONDS);
        return new WsTestClient(socket, frames);
    }

    public void send(Command command, MessageLite body) throws Exception {
        send(command, body, "req-" + sequence.get());
    }

    public void send(Command command, MessageLite body, String requestId) throws Exception {
        byte[] payload = EnvelopeCodec.encode(EnvelopeCodec.envelope(command, requestId, "ws-test-trace",
                body.toByteArray()));
        ByteBuf out = Unpooled.buffer();
        VeryalFrameEncoder.writeFrame(new ProtocolFrame(command, ProtocolConstants.PROTOCOL_VERSION,
                ProtocolConstants.FLAG_PROTOBUF, sequence.incrementAndGet(), payload), out);
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        out.release();
        socket.sendBinary(ByteBuffer.wrap(bytes), true).get(5, TimeUnit.SECONDS);
    }

    public ProtocolMessage read() throws Exception {
        byte[] frame = frames.poll(5, TimeUnit.SECONDS);
        if (frame == null) {
            throw new IllegalStateException("timed out reading frame");
        }
        return decode(frame);
    }

    public ProtocolMessage await(Command expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            byte[] frame = frames.poll(100, TimeUnit.MILLISECONDS);
            if (frame == null) {
                continue;
            }
            ProtocolMessage message = decode(frame);
            if (message.command() == expected) {
                return message;
            }
        }
        throw new IllegalStateException("timed out waiting for " + expected);
    }

    public <T extends MessageLite> T awaitPayload(Command expected, Parser<T> parser) throws Exception {
        return EnvelopeCodec.parsePayload(await(expected).payload(), parser);
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            socket.abort();
        }
    }

    private static ProtocolMessage decode(byte[] frame) {
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        buffer.getInt();                       // magic
        buffer.get();                          // protocol version
        buffer.get();                          // flags
        buffer.getShort();                     // command
        long frameSequence = Integer.toUnsignedLong(buffer.getInt());
        int bodyLength = buffer.getInt();
        byte[] payload = new byte[bodyLength];
        buffer.get(payload);
        return EnvelopeCodec.toMessage(EnvelopeCodec.decode(payload), frameSequence);
    }

    private static final class Collector implements WebSocket.Listener {
        private final BlockingQueue<byte[]> frames;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private Collector(BlockingQueue<byte[]> frames) {
            this.frames = frames;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            buffer.writeBytes(chunk);
            if (last) {
                frames.add(buffer.toByteArray());
                buffer.reset();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            return null;
        }
    }
}
