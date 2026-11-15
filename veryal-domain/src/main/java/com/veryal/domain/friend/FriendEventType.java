package com.veryal.domain.friend;

/**
 * 好友实时通知类型。
 */
public enum FriendEventType {

    REQUEST(1),
    ACCEPTED(2),
    REJECTED(3),
    CANCELED(4);

    private final int code;

    FriendEventType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
