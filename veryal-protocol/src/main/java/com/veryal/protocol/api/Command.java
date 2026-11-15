package com.veryal.protocol.api;

import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.ProtocolException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * VERYAL 协议指令。
 *
 * <p>指令码一经发布不可复用；新增指令只能追加。</p>
 */
public enum Command {

    HEARTBEAT(0x0001, false),
    HEARTBEAT_ACK(0x0002, false),

    LOGIN(0x0010, false),
    LOGIN_RESPONSE(0x0011, false),
    LOGOUT(0x0012, true),

    SEND_MESSAGE(0x0100, true),
    SERVER_ACK(0x0101, true),
    MESSAGE_NOTIFY(0x0102, true),
    MESSAGE_RECALL(0x0103, false),
    DELIVERY_ACK(0x0110, true),
    READ_ACK(0x0111, true),
    READ_NOTIFY(0x0112, false),

    ERROR(0x01FF, false);

    private static final Map<Integer, Command> BY_CODE;

    static {
        Map<Integer, Command> map = new HashMap<>();
        for (Command command : values()) {
            map.put(command.code, command);
        }
        BY_CODE = Collections.unmodifiableMap(map);
    }

    private final int code;
    private final boolean requiresAuthentication;

    Command(int code, boolean requiresAuthentication) {
        this.code = code;
        this.requiresAuthentication = requiresAuthentication;
    }

    public int code() {
        return code;
    }

    public boolean requiresAuthentication() {
        return requiresAuthentication;
    }

    public static Command fromCode(int code) {
        Command command = BY_CODE.get(code);
        if (command == null) {
            throw new ProtocolException(ErrorCode.UNSUPPORTED_COMMAND,
                    "unsupported command code: 0x" + Integer.toHexString(code));
        }
        return command;
    }

    public static boolean isKnown(int code) {
        return BY_CODE.containsKey(code);
    }
}
