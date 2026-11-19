package com.veryal.domain.moment;

/**
 * 互动通知类型。
 */
public enum MomentNoticeType {

    LIKE(1),
    COMMENT(2),
    REPLY(3);

    private final int code;

    MomentNoticeType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MomentNoticeType fromCode(int code) {
        return switch (code) {
            case 1 -> LIKE;
            case 3 -> REPLY;
            default -> COMMENT;
        };
    }
}
