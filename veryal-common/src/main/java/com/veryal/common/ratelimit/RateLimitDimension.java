package com.veryal.common.ratelimit;

/**
 * 限流维度。
 */
public enum RateLimitDimension {

    /** 客户端 IP。 */
    IP,
    /** 当前登录用户。 */
    USER,
    /** 当前租户。 */
    TENANT,
    /** 全局单桶。 */
    GLOBAL,
    /** 由 key 的 SpEL 表达式决定。 */
    CUSTOM
}
