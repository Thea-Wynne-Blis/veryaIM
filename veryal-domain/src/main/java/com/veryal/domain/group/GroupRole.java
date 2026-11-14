package com.veryal.domain.group;

/**
 * 群成员角色。
 */
public enum GroupRole {

    MEMBER(0),
    ADMIN(1),
    OWNER(2);

    private final int code;

    GroupRole(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean manager() {
        return this == OWNER || this == ADMIN;
    }

    public static GroupRole fromCode(int code) {
        for (GroupRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown group role: " + code);
    }
}
