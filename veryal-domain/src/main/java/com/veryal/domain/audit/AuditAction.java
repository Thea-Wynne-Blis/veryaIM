package com.veryal.domain.audit;

/**
 * 审计动作。字符串值写入审计表，禁止随意重命名。
 */
public enum AuditAction {

    LOGIN,
    GROUP_CREATE,
    GROUP_RENAME,
    GROUP_MEMBER_ADD,
    GROUP_MEMBER_REMOVE,
    GROUP_MEMBER_LEAVE,
    GROUP_ROLE_CHANGE,
    GROUP_OWNER_TRANSFER,
    GROUP_DISBAND
}
