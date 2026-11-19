package com.veryal.domain.moment;

/**
 * 动态可见范围。
 */
public enum MomentVisibility {

    /** 所有好友可见 */
    ALL_FRIENDS(0),
    /** 仅作者本人可见 */
    PRIVATE(1),
    /** 仅名单内好友可见 */
    PARTIAL(2),
    /** 名单内的好友不可见，其余好友可见 */
    EXCLUDE(3);

    private final int code;

    MomentVisibility(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MomentVisibility fromCode(int code) {
        for (MomentVisibility value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return ALL_FRIENDS;
    }
}
