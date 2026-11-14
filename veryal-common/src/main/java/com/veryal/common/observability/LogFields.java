package com.veryal.common.observability;

/**
 * 统一日志字段名，禁止在业务代码中散落字符串字面量。
 */
public final class LogFields {

    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String DEVICE_ID = "deviceId";
    public static final String CONNECTION_ID = "connectionId";
    public static final String GATEWAY_ID = "gatewayId";
    public static final String MESSAGE_ID = "messageId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String COMMAND = "command";

    private LogFields() {
    }
}
