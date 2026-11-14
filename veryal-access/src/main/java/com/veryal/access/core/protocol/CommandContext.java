package com.veryal.access.core.protocol;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.veryal.access.core.Connection;
import com.veryal.common.exception.ErrorCode;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.EnvelopeCodec;

/**
 * 一次业务命令的处理上下文。只允许访问当前连接与请求，禁止跨连接共享。
 */
public final class CommandContext {

    private final Connection connection;
    private final ProtocolMessage request;

    public CommandContext(Connection connection, ProtocolMessage request) {
        this.connection = connection;
        this.request = request;
    }

    public <T extends MessageLite> T payload(Parser<T> parser) {
        return EnvelopeCodec.parsePayload(request.payload(), parser);
    }

    public void reply(Command command, MessageLite body) {
        connection.send(ProtocolMessages.response(command, connection.nextSequence(), request, body));
    }

    public void replyError(ErrorCode errorCode, String message) {
        connection.send(ProtocolMessages.error(connection.nextSequence(), request, errorCode, message));
    }

    public Connection connection() {
        return connection;
    }

    public ProtocolMessage request() {
        return request;
    }
}
