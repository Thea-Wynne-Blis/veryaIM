package com.veryal.domain.conversation;

/**
 * 会话类型。
 */
public enum ConversationType {

    PRIVATE(1),
    GROUP(2);

    private final int code;

    ConversationType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ConversationType fromCode(int code) {
        for (ConversationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown conversation type: " + code);
    }
}
