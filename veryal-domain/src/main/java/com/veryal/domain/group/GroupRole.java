package com.veryal.domain.group;

/**
 * 群成员角色。
 *
 * <p>权限矩阵（后端为准，前端只负责隐藏入口）：</p>
 * <ul>
 *   <li>OWNER：踢任何非群主成员、禁言任何人、设撤管理员、转让、解散、改群资料</li>
 *   <li>ADMIN：踢普通成员、改群名/公告；<b>不能</b>禁言、不能动管理员、不能转让或解散</li>
 *   <li>MEMBER：无管理权限</li>
 * </ul>
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

    public boolean owner() {
        return this == OWNER;
    }

    /**
     * 当前角色能否把 {@code target} 移出群聊。
     */
    public boolean canKick(GroupRole target) {
        return switch (this) {
            case OWNER -> target != OWNER;
            case ADMIN -> target == MEMBER;
            case MEMBER -> false;
        };
    }

    /**
     * 当前角色能否禁言 {@code target}。禁言是群主专属能力。
     */
    public boolean canMute(GroupRole target) {
        return this == OWNER && target != OWNER;
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
