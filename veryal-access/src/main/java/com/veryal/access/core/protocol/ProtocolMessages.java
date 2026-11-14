package com.veryal.access.core.protocol;

import com.google.protobuf.MessageLite;
import com.veryal.common.exception.ErrorCode;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.api.ProtocolConstants;
import com.veryal.protocol.api.ProtocolMessage;
import com.veryal.protocol.protobuf.ErrorResponse;

/**
 * 服务端协议消息构造工具。
 */
public final class ProtocolMessages {

    private ProtocolMessages() {
    }

    public static ProtocolMessage response(Command command, long sequence, ProtocolMessage request,
                                           MessageLite body) {
        return new ProtocolMessage(command, ProtocolConstants.PROTOCOL_VERSION, sequence,
                request == null ? null : request.requestId(),
                request == null ? null : request.traceId(),
                body == null ? new byte[0] : body.toByteArray());
    }

    public static ProtocolMessage error(long sequence, ProtocolMessage request, ErrorCode errorCode, String message) {
        ErrorResponse body = ErrorResponse.newBuilder()
                .setCode(errorCode.code())
                .setMessage(message == null ? errorCode.message() : message)
                .setRequestId(request == null || request.requestId() == null ? "" : request.requestId())
                .build();
        return new ProtocolMessage(Command.ERROR, ProtocolConstants.PROTOCOL_VERSION, sequence,
                request == null ? null : request.requestId(),
                request == null ? null : request.traceId(),
                body.toByteArray());
    }
}
