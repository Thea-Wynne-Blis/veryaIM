package com.veryal.domain.message;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;

/**
 * 消息类型。SYSTEM 只能由服务端产生。
 */
public enum MessageType {

    TEXT(1, true),
    IMAGE(2, true),
    FILE(3, true),
    AUDIO(4, true),
    VIDEO(5, true),
    SYSTEM(9, false);

    private final int code;
    private final boolean userSendable;

    MessageType(int code, boolean userSendable) {
        this.code = code;
        this.userSendable = userSendable;
    }

    public int code() {
        return code;
    }

    public boolean userSendable() {
        return userSendable;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                "unsupported message type: " + code);
    }
}
