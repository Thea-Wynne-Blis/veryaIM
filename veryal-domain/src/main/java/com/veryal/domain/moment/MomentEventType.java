package com.veryal.domain.moment;

/**
 * 朋友圈实时事件类型。与通知类型保持一一对应。
 */
public enum MomentEventType {

    LIKE(1),
    COMMENT(2),
    REPLY(3);

    private final int code;

    MomentEventType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MomentEventType from(MomentNoticeType type) {
        return switch (type) {
            case LIKE -> LIKE;
            case COMMENT -> COMMENT;
            case REPLY -> REPLY;
        };
    }
}
