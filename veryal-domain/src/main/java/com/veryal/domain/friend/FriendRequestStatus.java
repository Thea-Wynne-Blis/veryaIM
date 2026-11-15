package com.veryal.domain.friend;

/**
 * 好友申请状态。
 */
public enum FriendRequestStatus {

    PENDING(0),
    ACCEPTED(1),
    REJECTED(2),
    CANCELED(3);

    private final int code;

    FriendRequestStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static FriendRequestStatus fromCode(int code) {
        for (FriendRequestStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return PENDING;
    }
}
