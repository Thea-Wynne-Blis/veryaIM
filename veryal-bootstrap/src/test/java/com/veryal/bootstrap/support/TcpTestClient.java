package com.veryal.bootstrap.support;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.EnvelopeCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 裸 TCP 测试客户端：直接按 16 字节协议头读写，用于端到端验证拆包、认证、ACK 与推送。
 */
public final class TcpTestClient implements AutoCloseable {

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final AtomicLong sequence = new AtomicLong();

    private TcpTestClient(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    public static TcpTestClient connect(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
        socket.setSoTimeout(5000);
        socket.setTcpNoDelay(true);
        return new TcpTestClient(socket);
    }

    public void send(Command command, MessageLite body) throws IOException {
        send(command, body, "req-" + sequence.get());
    }

    public void send(Command command, MessageLite body, String requestId) throws IOException {
        byte[] payload = EnvelopeCodec.encode(EnvelopeCodec.envelope(command, requestId, "tcp-test-trace",
                body.toByteArray()));
        ByteBuffer header = ByteBuffer.allocate(ProtocolConstants.HEADER_LENGTH);
        header.putInt(ProtocolConstants.MAGIC);
        header.put((byte) ProtocolConstants.PROTOCOL_VERSION);
        header.put((byte) ProtocolConstants.FLAG_PROTOBUF);
        header.putShort((short) command.code());
        header.putInt((int) sequence.incrementAndGet());
        header.putInt(payload.length);
        output.write(header.array());
        output.write(payload);
        output.flush();
    }

    public ProtocolMessage read() throws IOException {
        byte[] header = new byte[ProtocolConstants.HEADER_LENGTH];
        input.readFully(header);
        ByteBuffer buffer = ByteBuffer.wrap(header);
        int magic = buffer.getInt();
        if (magic != ProtocolConstants.MAGIC) {
            throw new IOException("invalid magic: " + Integer.toHexString(magic));
        }
        buffer.get();
        buffer.get();
        int commandCode = buffer.getShort() & 0xFFFF;
        long frameSequence = Integer.toUnsignedLong(buffer.getInt());
        int bodyLength = buffer.getInt();
        byte[] payload = new byte[bodyLength];
        input.readFully(payload);
        return EnvelopeCodec.toMessage(EnvelopeCodec.decode(payload), frameSequence);
    }

    /**
     * 读取直到收到指定指令，其它帧被忽略。
     */
    public ProtocolMessage await(Command expected) throws IOException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                ProtocolMessage message = read();
                if (message.command() == expected) {
                    return message;
                }
            } catch (SocketTimeoutException e) {
                break;
            }
        }
        throw new SocketTimeoutException("timed out waiting for " + expected);
    }

    public <T extends MessageLite> T awaitPayload(Command expected, Parser<T> parser) throws IOException {
        return EnvelopeCodec.parsePayload(await(expected).payload(), parser);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
